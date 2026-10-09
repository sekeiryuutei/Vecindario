package com.codevam.vecindad.budget.domain;

import java.math.BigDecimal;
import java.util.List;

public record BudgetSummary(
        AnnualBudget budget,
        List<BudgetItem> items,
        BigDecimal totalIncomeBudgeted,
        BigDecimal totalIncomeExecuted,
        BigDecimal totalExpenseBudgeted,
        BigDecimal totalExpenseExecuted,
        BigDecimal netBudgeted,
        BigDecimal netExecuted
) {}

