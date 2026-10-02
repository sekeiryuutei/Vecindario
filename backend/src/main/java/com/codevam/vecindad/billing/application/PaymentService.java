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
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Pagos. Un pago NUNCA se edita: se corrige revirtiéndolo (con motivo, libro y auditoría). Registrar es idempotente
 * por Idempotency-Key y atómico: el inmueble se bloquea, se imputa según la configuración y se asienta en el libro.
 * El mismo caso de uso lo usarán la conciliación bancaria y Wompi (source distinto de MANUAL).
 */
@Service
public class PaymentService {
    private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9._:\\-]{1,100}$");

    public record RegisterCommand(UUID unitId, BigDecimal amount, LocalDate paymentDate, PaymentMethod method, String source,
                                  String reference, String payerName, String notes) {}
    public record PaymentResult(Payment payment, List<AllocationView> allocations, boolean replayed) {}

    private final BillingPort billing;
    private final BillingSettingsPort settings;
    private final PaymentApplication application;
    private final FinanceTx tx;
    private final PropertyUnitPort units;
    private final AuditService audit;
    private final Clock clock;

    public PaymentService(BillingPort billing, BillingSettingsPort settings, PaymentApplication application, FinanceTx tx,
                          PropertyUnitPort units, AuditService audit, Clock clock) {
        this.billing = billing;
        this.settings = settings;
        this.application = application;
        this.tx = tx;
        this.units = units;
        this.audit = audit;
        this.clock = clock;
    }

    public PaymentResult register(RegisterCommand c, String idempotencyKey) {
        UUID tenantId = TenantContext.require().id();
        BigDecimal amount = Money.positive(c.amount(), "el pago");
        if (idempotencyKey != null && !KEY.matcher(idempotencyKey).matches()) {
            throw ApiException.badRequest("INVALID_IDEMPOTENCY_KEY", "La Idempotency-Key solo admite letras, números y . _ : - (máximo 100).");
        }
        if (c.method() == null) {
            throw ApiException.badRequest("INVALID_METHOD", "Indica el medio de pago.");
        }
        if ((c.source() == null || "MANUAL".equals(c.source())) && c.method() == PaymentMethod.WOMPI) {
            throw ApiException.badRequest("INVALID_METHOD", "Los pagos Wompi se registran solo a través de la pasarela, no manualmente.");
        }
        LocalDate today = Money.today(clock);
        LocalDate date = c.paymentDate() == null ? today : c.paymentDate();
        if (date.isAfter(today)) {
            throw ApiException.badRequest("INVALID_PAYMENT_DATE", "La fecha del pago no puede estar en el futuro.");
        }
        units.findActiveById(c.unitId()).orElseThrow(() -> ApiException.notFound("El inmueble no existe."));
        UUID actor = CurrentUser.get().map(AuthenticatedUser::userId).orElse(null);
        String source = c.source() == null ? "MANUAL" : c.source();
        UUID id = UUID.randomUUID();

        PaymentResult result = tx.run(() -> {
            billing.lockUnit(c.unitId());
            if (idempotencyKey != null) {
                var existing = billing.findPaymentByKey(idempotencyKey);
                if (existing.isPresent()) return replay(existing.get(), c.unitId(), amount);
            }
            Payment draft = new Payment(id, c.unitId(), null, amount, amount, date, c.method(), source, blank(c.reference()),
                    blank(c.payerName()), blank(c.notes()), "APPLIED", null, null, null);
            if (!billing.insertPayment(draft, idempotencyKey, actor)) {
                // Otra solicitud con la misma llave ganó la carrera: se devuelve ese resultado.
                return replay(billing.findPaymentByKey(idempotencyKey).orElseThrow(), c.unitId(), amount);
            }
            BigDecimal remainder = application.applyToOpenCharges(id, c.unitId(), amount, settings.load());
            billing.setUnapplied(id, remainder);
            billing.addLedger(BillingService.ledger(c.unitId(), "PAYMENT", amount.negate(), date, null, id, null,
                    "Pago " + c.method().name() + (blank(c.reference()) != null ? " ref. " + c.reference().trim() : ""), null, actor));
            return new PaymentResult(billing.findPayment(id).orElseThrow(), billing.allocationsOf(id, true), false);
        });
        if (!result.replayed()) {
            audit.log(tenantId, "PAYMENT_REGISTERED", "PAYMENT", result.payment().id().toString(), true,
                    Map.of("unitId", c.unitId().toString(), "amount", amount.toPlainString(), "method", c.method().name(),
                            "unapplied", result.payment().unappliedAmount().toPlainString()));
        }
        return result;
    }

    public PaymentResult reverse(UUID paymentId, String reason) {
        UUID tenantId = TenantContext.require().id();
        Payment p = get(paymentId);
        UUID actor = CurrentUser.get().map(AuthenticatedUser::userId).orElse(null);
        LocalDate today = Money.today(clock);
        tx.run(() -> {
            billing.lockUnit(p.unitId());
            Payment cur = billing.findPayment(paymentId).orElseThrow();
            if (!"APPLIED".equals(cur.status())) {
                throw ApiException.conflict("PAYMENT_ALREADY_REVERSED", "El pago ya fue revertido.");
            }
            for (AllocationView a : billing.allocationsOf(paymentId, true)) {
                if (!billing.subPaid(a.chargeId(), a.amount())) {
                    throw new IllegalStateException("Reversión inconsistente sobre el cargo " + a.chargeId());
                }
            }
            billing.reverseAllocations(paymentId);
            billing.markReversed(paymentId, reason.trim(), actor);
            billing.addLedger(BillingService.ledger(p.unitId(), "REVERSAL", cur.amount(), today, null, paymentId, null,
                    "Reversión de pago", reason.trim(), actor));
            application.reapplyCredit(p.unitId());
            return null;
        });
        audit.log(tenantId, "PAYMENT_REVERSED", "PAYMENT", paymentId.toString(), true,
                Map.of("amount", p.amount().toPlainString(), "reason", reason.trim()));
        return new PaymentResult(get(paymentId), billing.allocationsOf(paymentId, false), false);
    }

    public PaymentResult getWithAllocations(UUID id) {
        return new PaymentResult(get(id), billing.allocationsOf(id, false), false);
    }

    public Payment get(UUID id) {
        TenantContext.require();
        return billing.findPayment(id).orElseThrow(() -> ApiException.notFound("El pago no existe."));
    }

    public PageResult<Payment> search(UUID unitId, LocalDate from, LocalDate to, int page, int size) {
        TenantContext.require();
        return billing.searchPayments(unitId, from, to, Paging.page(page), Paging.size(size));
    }

    private PaymentResult replay(Payment existing, UUID unitId, BigDecimal amount) {
        if (!existing.unitId().equals(unitId) || existing.amount().compareTo(amount) != 0) {
            throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED", "Esa Idempotency-Key ya se usó con un pago distinto.");
        }
        return new PaymentResult(existing, billing.allocationsOf(existing.id(), true), true);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
