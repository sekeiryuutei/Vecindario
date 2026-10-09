package com.codevam.vecindad.reports.domain;

import java.math.BigDecimal;

public record BudgetReport(
        int year,
        String title,
        BigDecimal incomeBudgeted,
        BigDecimal incomeExecuted,
        BigDecimal expenseBudgeted,
        BigDecimal expenseExecuted,
        BigDecimal netBudgeted,
        BigDecimal netExecuted
) {}

