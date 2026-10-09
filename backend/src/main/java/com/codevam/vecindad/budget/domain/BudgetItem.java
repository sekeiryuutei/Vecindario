package com.codevam.vecindad.budget.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BudgetItem(
        UUID id,
        UUID budgetId,
        String code,
        String name,
        BudgetType type,
        BigDecimal budgetedAmount,
        BigDecimal executedAmount,
        Instant createdAt,
        Instant updatedAt
) {}

