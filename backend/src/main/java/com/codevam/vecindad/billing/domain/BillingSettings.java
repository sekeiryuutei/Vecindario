package com.codevam.vecindad.billing.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public record BillingSettings(List<ConceptType> allocationOrder, boolean oldestFirst, boolean interestEnabled,
                              BigDecimal interestMonthlyRate, int graceDays, boolean blockReservationsWhenOverdue,
                              int overdueDaysForBlock) {

    /** Orden configurado + los tipos no mencionados al final (en el orden por defecto), para que nada quede sin imputar. */
    public List<ConceptType> effectiveOrder() {
        List<ConceptType> out = new ArrayList<>(allocationOrder);
        for (ConceptType t : ConceptType.DEFAULT_ORDER) {
            if (!out.contains(t)) out.add(t);
        }
        return out;
    }
}
