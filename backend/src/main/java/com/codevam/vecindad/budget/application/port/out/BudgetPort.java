package com.codevam.vecindad.budget.application.port.out;

import com.codevam.vecindad.budget.domain.AnnualBudget;
import com.codevam.vecindad.budget.domain.BudgetExecution;
import com.codevam.vecindad.budget.domain.BudgetItem;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetPort {
    AnnualBudget insertBudget(AnnualBudget budget);
    Optional<AnnualBudget> findBudgetById(UUID id);
    Optional<AnnualBudget> findBudgetByYear(int year);
    List<AnnualBudget> listBudgets();
    boolean updateBudgetTotals(UUID budgetId, BigDecimal incomeBudgeted, BigDecimal expenseBudgeted);

    BudgetItem insertItem(BudgetItem item);
    Optional<BudgetItem> findItemById(UUID id);
    List<BudgetItem> listItemsByBudget(UUID budgetId);
    boolean updateItemExecutedAmount(UUID itemId, BigDecimal executedAmount);

    BudgetExecution insertExecution(BudgetExecution execution);
    List<BudgetExecution> listExecutionsByItem(UUID itemId);
    List<BudgetExecution> listExecutionsByBudget(UUID budgetId);
}

