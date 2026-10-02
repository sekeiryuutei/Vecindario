package com.codevam.vecindad.billing.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** state: OPEN | PARTIAL | PAID | VOIDED. outstanding = amount + adjustedAmount - paidAmount (0 si está anulado). */
public record Charge(UUID id, UUID unitId, String unitIdentifier, ConceptType type, String description, LocalDate period,
                     LocalDate issueDate, LocalDate dueDate, BigDecimal amount, BigDecimal adjustedAmount,
                     BigDecimal paidAmount, BigDecimal outstanding, String state, LocalDate interestAccruedUntil,
                     Instant voidedAt, String voidReason, Instant createdAt) {

    public boolean overdue(LocalDate today) {
        return outstanding.signum() > 0 && dueDate.isBefore(today);
    }
}
