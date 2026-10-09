package com.codevam.vecindad.budget.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AnnualBudget(
        UUID id,
        int year,
        String title,
        BudgetStatus status,
        BigDecimal totalIncomeBudgeted,
        BigDecimal totalExpenseBudgeted,
        Instant createdAt,
        Instant updatedAt
) {}

