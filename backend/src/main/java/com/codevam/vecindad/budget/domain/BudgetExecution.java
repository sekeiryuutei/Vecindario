package com.codevam.vecindad.budget.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record BudgetExecution(
        UUID id,
        UUID budgetItemId,
        UUID workOrderId,
        UUID supplierId,
        BigDecimal amount,
        LocalDate executionDate,
        String invoiceNumber,
        String description,
        UUID registeredBy,
        Instant createdAt
) {}

