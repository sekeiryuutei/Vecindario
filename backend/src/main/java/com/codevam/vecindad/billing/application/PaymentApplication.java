package com.codevam.vecindad.billing.application;

import com.codevam.vecindad.billing.application.port.out.BillingPort;
import com.codevam.vecindad.billing.application.port.out.BillingSettingsPort;
import com.codevam.vecindad.billing.domain.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Aplica dinero a cargos según la configuración. Debe invocarse DENTRO de FinanceTx y con el inmueble bloqueado. */
@Component
public class PaymentApplication {
    private final BillingPort billing;
    private final BillingSettingsPort settings;

    public PaymentApplication(BillingPort billing, BillingSettingsPort settings) {
        this.billing = billing;
        this.settings = settings;
    }

    /** Imputa `amount` del pago a los cargos pendientes del inmueble; devuelve lo que sobra (saldo a favor). */
    public BigDecimal applyToOpenCharges(UUID paymentId, UUID unitId, BigDecimal amount, BillingSettings st) {
        List<PaymentAllocator.OpenCharge> open = billing.openCharges(unitId).stream()
                .map(c -> new PaymentAllocator.OpenCharge(c.id(), c.type(), c.dueDate(), c.outstanding())).toList();
        PaymentAllocator.Result r = PaymentAllocator.allocate(amount, open, st.effectiveOrder(), st.oldestFirst());
        for (PaymentAllocator.Allocation a : r.allocations()) {
            if (!billing.addPaid(a.chargeId(), a.amount())) {
                throw new IllegalStateException("Imputación inconsistente sobre el cargo " + a.chargeId());
            }
            billing.insertAllocation(paymentId, a.chargeId(), a.amount());
        }
        return r.remainder();
    }

    /** Si el inmueble tiene saldo a favor sin imputar y aparecen cargos nuevos, lo aplica automáticamente. */
    public void reapplyCredit(UUID unitId) {
        List<Payment> credits = billing.paymentsWithCredit(unitId);
        if (credits.isEmpty()) return;
        BillingSettings st = settings.load();
        for (Payment p : credits) {
            BigDecimal remainder = applyToOpenCharges(p.id(), unitId, p.unappliedAmount(), st);
            if (remainder.compareTo(p.unappliedAmount()) != 0) {
                billing.setUnapplied(p.id(), remainder);
            }
        }
    }
}
