package com.codevam.vecindad.billing.application;

import com.codevam.vecindad.billing.application.port.out.BillingPort;
import com.codevam.vecindad.billing.domain.*;
import com.codevam.vecindad.people.application.port.out.UnitRelationPort;
import com.codevam.vecindad.properties.application.port.out.PropertyUnitPort;
import com.codevam.vecindad.properties.domain.PropertyUnit;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.security.AuthenticatedUser;
import com.codevam.vecindad.shared.security.CurrentUser;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class StatementService {
    private final BillingPort billing;
    private final PropertyUnitPort units;
    private final UnitRelationPort relations;
    private final Clock clock;

    public StatementService(BillingPort billing, PropertyUnitPort units, UnitRelationPort relations, Clock clock) {
        this.billing = billing;
        this.units = units;
        this.relations = relations;
        this.clock = clock;
    }

    /** Vista administrativa/contable de cualquier inmueble de la copropiedad activa. */
    public Statement statement(UUID unitId, LocalDate from, LocalDate to) {
        TenantContext.require();
        return build(unitId, from, to);
    }

    /** Vista del residente: solo inmuebles con los que su persona tiene una relación vigente (404 si no). */
    public Statement myStatement(UUID unitId, LocalDate from, LocalDate to) {
        AuthenticatedUser me = CurrentUser.requireTenant();
        if (unitId == null || !relations.userHasUnit(me.userId(), unitId)) {
            throw ApiException.notFound("El inmueble no existe.");
        }
        return build(unitId, from, to);
    }

    private Statement build(UUID unitId, LocalDate from, LocalDate to) {
        PropertyUnit unit = units.findActiveById(unitId).orElseThrow(() -> ApiException.notFound("El inmueble no existe."));
        LocalDate today = Money.today(clock);
        LocalDate f = from == null ? today.withDayOfMonth(1) : from;
        LocalDate t = to == null ? today : to;
        if (t.isBefore(f) || ChronoUnit.DAYS.between(f, t) > 366L * 5) {
            throw ApiException.badRequest("INVALID_RANGE", "El rango de fechas es inválido (máximo 5 años).");
        }
        BigDecimal opening = billing.ledgerBalanceBefore(unitId, f);
        List<LedgerEntry> lines = billing.ledgerBetween(unitId, f, t);
        BigDecimal charges = BigDecimal.ZERO, interest = BigDecimal.ZERO, payments = BigDecimal.ZERO, adjustments = BigDecimal.ZERO, net = BigDecimal.ZERO;
        for (LedgerEntry e : lines) {
            net = net.add(e.amount());
            switch (e.entryType()) {
                case "CHARGE" -> {
                    if ("INTEREST".equals(e.conceptType())) interest = interest.add(e.amount()); else charges = charges.add(e.amount());
                }
                case "PAYMENT", "REVERSAL" -> payments = payments.add(e.amount());
                default -> adjustments = adjustments.add(e.amount());
            }
        }
        List<Charge> open = billing.openCharges(unitId);
        BigDecimal overdue = open.stream().filter(c -> c.dueDate().isBefore(today)).map(Charge::outstanding).reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDate next = open.stream().map(Charge::dueDate).filter(d -> !d.isBefore(today)).min(LocalDate::compareTo).orElse(null);
        return new Statement(unitId, unit.identifier(), f, t, opening, charges, interest, payments, adjustments, opening.add(net),
                overdue, next, billing.creditBalance(unitId), lines, open);
    }
}
