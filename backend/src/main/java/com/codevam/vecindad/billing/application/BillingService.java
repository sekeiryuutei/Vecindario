package com.codevam.vecindad.billing.application;

import com.codevam.vecindad.audit.application.AuditService;
import com.codevam.vecindad.billing.application.port.out.BillingPort;
import com.codevam.vecindad.billing.application.port.out.BillingSettingsPort;
import com.codevam.vecindad.billing.domain.*;
import com.codevam.vecindad.properties.application.port.out.PropertyUnitPort;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.model.Paging;
import com.codevam.vecindad.shared.security.AuthenticatedUser;
import com.codevam.vecindad.shared.security.CurrentUser;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Cargos, facturación masiva idempotente, intereses de mora, notas crédito/débito y anulaciones. */
@Service
public class BillingService {

    public enum AdjustmentKind { CREDIT, DEBIT }

    public record ChargeCommand(UUID unitId, ConceptType type, String description, LocalDate period, LocalDate issueDate,
                                LocalDate dueDate, BigDecimal amount) {}
    /** Exactamente uno de totalBudget (se reparte por coeficiente) o fixedAmount (igual para todos). */
    public record OrdinaryRunCommand(LocalDate period, LocalDate dueDate, BigDecimal totalBudget, BigDecimal fixedAmount) {}
    public record RunSummary(LocalDate period, int created, int skippedExisting, int skippedNoCoefficient, BigDecimal totalAmount) {}
    public record InterestRunSummary(LocalDate asOf, int unitsCharged, BigDecimal totalInterest) {}

    private final BillingPort billing;
    private final BillingSettingsPort settings;
    private final PaymentApplication application;
    private final FinanceTx tx;
    private final PropertyUnitPort units;
    private final AuditService audit;
    private final Clock clock;

    public BillingService(BillingPort billing, BillingSettingsPort settings, PaymentApplication application, FinanceTx tx,
                          PropertyUnitPort units, AuditService audit, Clock clock) {
        this.billing = billing;
        this.settings = settings;
        this.application = application;
        this.tx = tx;
        this.units = units;
        this.audit = audit;
        this.clock = clock;
    }

    // ------------------------------------------------------------------- cargos

    public Charge createCharge(ChargeCommand cmd) {
        UUID tenantId = TenantContext.require().id();
        BigDecimal amount = Money.positive(cmd.amount(), "el cargo");
        units.findActiveById(cmd.unitId()).orElseThrow(() -> ApiException.notFound("El inmueble no existe."));
        LocalDate issue = cmd.issueDate() == null ? Money.today(clock) : cmd.issueDate();
        if (cmd.dueDate() == null || cmd.dueDate().isBefore(issue)) {
            throw ApiException.badRequest("INVALID_DUE_DATE", "La fecha de vencimiento es obligatoria y no puede ser anterior a la de emisión.");
        }
        LocalDate period = cmd.period() != null ? cmd.period().withDayOfMonth(1)
                : cmd.type() == ConceptType.ORDINARY ? issue.withDayOfMonth(1) : null;
        UUID actor = actor();
        UUID id = UUID.randomUUID();
        Charge saved = tx.run(() -> {
            billing.lockUnit(cmd.unitId());
            if (!billing.insertCharge(newCharge(id, cmd.unitId(), cmd.type(), cmd.description().trim(), period, issue, cmd.dueDate(), amount), actor)) {
                throw ApiException.conflict("CHARGE_ALREADY_EXISTS", "Ya existe la cuota ordinaria de ese inmueble para el periodo indicado.");
            }
            billing.addLedger(ledger(cmd.unitId(), "CHARGE", amount, issue, id, null, cmd.type().name(), cmd.description().trim(), null, actor));
            application.reapplyCredit(cmd.unitId());
            return billing.findCharge(id).orElseThrow();
        });
        audit.log(tenantId, "CHARGE_CREATED", "CHARGE", id.toString(), true,
                Map.of("unitId", cmd.unitId().toString(), "type", cmd.type().name(), "amount", amount.toPlainString()));
        return saved;
    }

    public RunSummary runOrdinary(OrdinaryRunCommand cmd) {
        UUID tenantId = TenantContext.require().id();
        if ((cmd.totalBudget() == null) == (cmd.fixedAmount() == null)) {
            throw ApiException.badRequest("INVALID_RUN", "Indica totalBudget (se reparte por coeficiente) o fixedAmount, pero no ambos.");
        }
        if (cmd.period() == null || cmd.dueDate() == null) {
            throw ApiException.badRequest("INVALID_RUN", "El periodo y la fecha de vencimiento son obligatorios.");
        }
        BigDecimal total = cmd.totalBudget() != null ? Money.positive(cmd.totalBudget(), "el presupuesto") : null;
        BigDecimal fixed = cmd.fixedAmount() != null ? Money.positive(cmd.fixedAmount(), "la cuota") : null;
        LocalDate period = cmd.period().withDayOfMonth(1);
        String description = String.format("Cuota de administración %02d/%d", period.getMonthValue(), period.getYear());
        UUID actor = actor();
        int created = 0, existing = 0, noCoef = 0;
        BigDecimal sum = BigDecimal.ZERO;
        for (BillableUnit u : billing.billableUnits()) {
            BigDecimal amount = fixed;
            if (amount == null) {
                if (u.coefficient() == null || u.coefficient().signum() <= 0) { noCoef++; continue; }
                amount = Money.round0(total.multiply(u.coefficient()).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
                if (amount.signum() <= 0) { noCoef++; continue; }
            }
            BigDecimal finalAmount = amount;
            UUID id = UUID.randomUUID();
            boolean inserted = tx.run(() -> {
                billing.lockUnit(u.id());
                if (!billing.insertCharge(newCharge(id, u.id(), ConceptType.ORDINARY, description, period, period, cmd.dueDate(), finalAmount), actor)) {
                    return false;
                }
                billing.addLedger(ledger(u.id(), "CHARGE", finalAmount, period, id, null, "ORDINARY", description, null, actor));
                application.reapplyCredit(u.id());
                return true;
            });
            if (inserted) { created++; sum = sum.add(finalAmount); } else { existing++; }
        }
        audit.log(tenantId, "BILLING_RUN_ORDINARY", "CHARGE", period.toString(), true,
                Map.of("created", created, "skippedExisting", existing, "skippedNoCoefficient", noCoef, "total", sum.toPlainString()));
        return new RunSummary(period, created, existing, noCoef, sum);
    }

    /** Intereses de mora: tasa mensual / 30 por día sobre el saldo de cuotas vencidas (más allá de la gracia). Idempotente por fecha. */
    public InterestRunSummary runInterest(LocalDate requestedAsOf) {
        UUID tenantId = TenantContext.require().id();
        BillingSettings st = settings.load();
        if (!st.interestEnabled() || st.interestMonthlyRate().signum() <= 0) {
            throw ApiException.conflict("INTEREST_DISABLED", "Los intereses de mora no están activados o la tasa es cero. Configúralos primero.");
        }
        LocalDate today = Money.today(clock);
        LocalDate asOf = requestedAsOf == null ? today : requestedAsOf;
        if (asOf.isAfter(today)) {
            throw ApiException.badRequest("INVALID_DATE", "La fecha de corte no puede estar en el futuro.");
        }
        BigDecimal dailyRate = st.interestMonthlyRate().divide(BigDecimal.valueOf(3000), 12, RoundingMode.HALF_UP); // %/100/30
        UUID actor = actor();
        int charged = 0;
        BigDecimal total = BigDecimal.ZERO;
        for (UUID unitId : billing.unitsWithInterestCandidates(asOf, st.graceDays())) {
            BigDecimal amount = tx.run(() -> {
                billing.lockUnit(unitId);
                List<Charge> candidates = billing.interestCandidates(unitId, asOf, st.graceDays());
                BigDecimal sum = BigDecimal.ZERO;
                List<UUID> accrued = new ArrayList<>();
                for (Charge c : candidates) {
                    LocalDate start = c.interestAccruedUntil() != null ? c.interestAccruedUntil() : c.dueDate().plusDays(st.graceDays());
                    long days = ChronoUnit.DAYS.between(start, asOf);
                    if (days <= 0) continue;
                    sum = sum.add(c.outstanding().multiply(dailyRate).multiply(BigDecimal.valueOf(days)));
                    accrued.add(c.id());
                }
                BigDecimal interest = Money.round0(sum);
                if (interest.signum() <= 0) return BigDecimal.ZERO;
                UUID id = UUID.randomUUID();
                String desc = String.format("Intereses de mora al %02d/%02d/%d", asOf.getDayOfMonth(), asOf.getMonthValue(), asOf.getYear());
                billing.insertCharge(newCharge(id, unitId, ConceptType.INTEREST, desc, null, asOf, asOf, interest), actor);
                billing.addLedger(ledger(unitId, "CHARGE", interest, asOf, id, null, "INTEREST", desc, null, actor));
                billing.markInterestAccrued(accrued, asOf);
                application.reapplyCredit(unitId);
                return interest;
            });
            if (amount.signum() > 0) { charged++; total = total.add(amount); }
        }
        audit.log(tenantId, "BILLING_RUN_INTEREST", "CHARGE", asOf.toString(), true,
                Map.of("unitsCharged", charged, "total", total.toPlainString()));
        return new InterestRunSummary(asOf, charged, total);
    }

    public Charge voidCharge(UUID id, String reason) {
        UUID tenantId = TenantContext.require().id();
        Charge c = get(id);
        UUID actor = actor();
        LocalDate today = Money.today(clock);
        tx.run(() -> {
            billing.lockUnit(c.unitId());
            Charge cur = billing.findCharge(id).orElseThrow();
            if (cur.voidedAt() != null) {
                throw ApiException.conflict("CHARGE_ALREADY_VOIDED", "El cargo ya estaba anulado.");
            }
            if (!billing.voidCharge(id, reason.trim(), actor)) {
                throw ApiException.conflict("CHARGE_HAS_PAYMENTS", "El cargo tiene pagos aplicados. Revierte los pagos antes de anularlo.");
            }
            billing.addLedger(ledger(c.unitId(), "VOID", cur.amount().add(cur.adjustedAmount()).negate(), today, id, null,
                    cur.type().name(), "Anulación: " + cur.description(), reason.trim(), actor));
            return null;
        });
        audit.log(tenantId, "CHARGE_VOIDED", "CHARGE", id.toString(), true, Map.of("reason", reason.trim()));
        return get(id);
    }

    /** Nota crédito (baja el cargo) o nota débito (lo sube). No se puede dejar el cargo por debajo de lo ya pagado. */
    public Charge adjust(UUID id, AdjustmentKind kind, BigDecimal rawAmount, String reason) {
        UUID tenantId = TenantContext.require().id();
        BigDecimal amount = Money.positive(rawAmount, "el ajuste");
        Charge c = get(id);
        UUID actor = actor();
        LocalDate today = Money.today(clock);
        BigDecimal delta = kind == AdjustmentKind.CREDIT ? amount.negate() : amount;
        tx.run(() -> {
            billing.lockUnit(c.unitId());
            if (!billing.adjustCharge(id, delta)) {
                throw ApiException.conflict("ADJUSTMENT_NOT_ALLOWED", kind == AdjustmentKind.CREDIT
                        ? "La nota crédito supera el saldo pendiente del cargo (o el cargo está anulado). Revierte primero los pagos aplicados."
                        : "No se puede ajustar un cargo anulado.");
            }
            billing.addLedger(ledger(c.unitId(), kind == AdjustmentKind.CREDIT ? "ADJUSTMENT_CREDIT" : "ADJUSTMENT_DEBIT", delta, today, id, null,
                    c.type().name(), (kind == AdjustmentKind.CREDIT ? "Nota crédito: " : "Nota débito: ") + c.description(), reason.trim(), actor));
            application.reapplyCredit(c.unitId());
            return null;
        });
        audit.log(tenantId, kind == AdjustmentKind.CREDIT ? "CHARGE_CREDIT_NOTE" : "CHARGE_DEBIT_NOTE", "CHARGE", id.toString(), true,
                Map.of("amount", amount.toPlainString(), "reason", reason.trim()));
        return get(id);
    }

    // ----------------------------------------------------------------- consultas

    public Charge get(UUID id) {
        TenantContext.require();
        return billing.findCharge(id).orElseThrow(() -> ApiException.notFound("El cargo no existe."));
    }

    public PageResult<Charge> search(UUID unitId, boolean pendingOnly, ConceptType type, int page, int size) {
        TenantContext.require();
        return billing.searchCharges(unitId, pendingOnly, type, Paging.page(page), Paging.size(size));
    }

    public PortfolioView portfolio(boolean overdueOnly, int page, int size) {
        TenantContext.require();
        return billing.portfolio(overdueOnly, Money.today(clock), Paging.page(page), Paging.size(size));
    }

    public PageResult<LedgerEntry> ledger(UUID unitId, int page, int size) {
        TenantContext.require();
        units.findActiveById(unitId).orElseThrow(() -> ApiException.notFound("El inmueble no existe."));
        return billing.ledger(unitId, Paging.page(page), Paging.size(size));
    }

    // ------------------------------------------------------------------ helpers

    private static Charge newCharge(UUID id, UUID unitId, ConceptType type, String description, LocalDate period,
                                    LocalDate issue, LocalDate due, BigDecimal amount) {
        return new Charge(id, unitId, null, type, description, period, issue, due, amount, BigDecimal.ZERO, BigDecimal.ZERO,
                amount, "OPEN", null, null, null, null);
    }

    static LedgerEntry ledger(UUID unitId, String type, BigDecimal amount, LocalDate date, UUID chargeId, UUID paymentId,
                              String concept, String description, String reason, UUID actor) {
        return new LedgerEntry(UUID.randomUUID(), unitId, type, amount, date, chargeId, paymentId, concept, description, reason, actor, null);
    }

    private static UUID actor() {
        return CurrentUser.get().map(AuthenticatedUser::userId).orElse(null);
    }
}
