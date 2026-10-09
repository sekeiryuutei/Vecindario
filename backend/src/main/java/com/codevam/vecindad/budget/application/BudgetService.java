package com.codevam.vecindad.budget.application;

import com.codevam.vecindad.budget.application.port.out.BudgetPort;
import com.codevam.vecindad.budget.domain.*;
import com.codevam.vecindad.shared.error.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class BudgetService {

    public record CreateBudgetCommand(
            int year,
            String title
    ) {}

    public record CreateItemCommand(
            String code,
            String name,
            BudgetType type,
            BigDecimal budgetedAmount
    ) {}

    public record ExecuteBudgetCommand(
            UUID workOrderId,
            UUID supplierId,
            BigDecimal amount,
            LocalDate executionDate,
            String invoiceNumber,
            String description
    ) {}

    private final BudgetPort port;

    public BudgetService(BudgetPort port) {
        this.port = port;
    }

    public List<AnnualBudget> listBudgets() {
        return port.listBudgets();
    }

    public AnnualBudget getBudgetById(UUID id) {
        return port.findBudgetById(id)
                .orElseThrow(() -> ApiException.notFound("Presupuesto no encontrado."));
    }

    public AnnualBudget getBudgetByYear(int year) {
        return port.findBudgetByYear(year)
                .orElseThrow(() -> ApiException.notFound("Presupuesto para el año " + year + " no encontrado."));
    }

    @Transactional
    public AnnualBudget createBudget(CreateBudgetCommand cmd) {
        if (port.findBudgetByYear(cmd.year()).isPresent()) {
            throw ApiException.conflict("BUDGET_YEAR_EXISTS", "Ya existe un presupuesto registrado para la vigencia " + cmd.year());
        }
        AnnualBudget budget = new AnnualBudget(
                UUID.randomUUID(),
                cmd.year(),
                cmd.title().trim(),
                BudgetStatus.APPROVED,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                Instant.now(),
                Instant.now()
        );
        return port.insertBudget(budget);
    }

    @Transactional
    public BudgetItem addItem(UUID budgetId, CreateItemCommand cmd) {
        AnnualBudget budget = getBudgetById(budgetId);
        if (budget.status() == BudgetStatus.CLOSED) {
            throw ApiException.conflict("BUDGET_CLOSED", "El presupuesto se encuentra cerrado y no admite nuevos rubros.");
        }

        BigDecimal budgeted = cmd.budgetedAmount() != null ? cmd.budgetedAmount() : BigDecimal.ZERO;

        BudgetItem item = new BudgetItem(
                UUID.randomUUID(),
                budgetId,
                cmd.code().trim(),
                cmd.name().trim(),
                cmd.type(),
                budgeted,
                BigDecimal.ZERO,
                Instant.now(),
                Instant.now()
        );
        BudgetItem saved = port.insertItem(item);

        // Recalcular totales del presupuesto
        recalculateBudgetTotals(budgetId);

        return saved;
    }

    public List<BudgetItem> listItems(UUID budgetId) {
        getBudgetById(budgetId);
        return port.listItemsByBudget(budgetId);
    }

    public BudgetItem getItemById(UUID itemId) {
        return port.findItemById(itemId)
                .orElseThrow(() -> ApiException.notFound("Rubro presupuestal no encontrado."));
    }

    @Transactional
    public BudgetExecution executeItem(UUID itemId, UUID userId, ExecuteBudgetCommand cmd) {
        BudgetItem item = getItemById(itemId);
        AnnualBudget budget = getBudgetById(item.budgetId());
        if (budget.status() == BudgetStatus.CLOSED) {
            throw ApiException.conflict("BUDGET_CLOSED", "El presupuesto para esta vigencia se encuentra cerrado.");
        }

        if (cmd.amount() == null || cmd.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("INVALID_AMOUNT", "El valor ejecutado debe ser mayor a cero.");
        }

        BudgetExecution exec = new BudgetExecution(
                UUID.randomUUID(),
                itemId,
                cmd.workOrderId(),
                cmd.supplierId(),
                cmd.amount(),
                cmd.executionDate() != null ? cmd.executionDate() : LocalDate.now(),
                cmd.invoiceNumber(),
                cmd.description().trim(),
                userId,
                Instant.now()
        );
        BudgetExecution saved = port.insertExecution(exec);

        // Actualizar monto ejecutado del rubro
        BigDecimal newExecuted = item.executedAmount().add(cmd.amount());
        port.updateItemExecutedAmount(itemId, newExecuted);

        return saved;
    }

    public List<BudgetExecution> listExecutionsByItem(UUID itemId) {
        getItemById(itemId);
        return port.listExecutionsByItem(itemId);
    }

    public BudgetSummary getSummary(int year) {
        AnnualBudget budget = getBudgetByYear(year);
        List<BudgetItem> items = port.listItemsByBudget(budget.id());

        BigDecimal incomeBudgeted = BigDecimal.ZERO;
        BigDecimal incomeExecuted = BigDecimal.ZERO;
        BigDecimal expenseBudgeted = BigDecimal.ZERO;
        BigDecimal expenseExecuted = BigDecimal.ZERO;

        for (BudgetItem i : items) {
            if (i.type() == BudgetType.INCOME) {
                incomeBudgeted = incomeBudgeted.add(i.budgetedAmount());
                incomeExecuted = incomeExecuted.add(i.executedAmount());
            } else {
                expenseBudgeted = expenseBudgeted.add(i.budgetedAmount());
                expenseExecuted = expenseExecuted.add(i.executedAmount());
            }
        }

        BigDecimal netBudgeted = incomeBudgeted.subtract(expenseBudgeted);
        BigDecimal netExecuted = incomeExecuted.subtract(expenseExecuted);

        return new BudgetSummary(
                budget,
                items,
                incomeBudgeted,
                incomeExecuted,
                expenseBudgeted,
                expenseExecuted,
                netBudgeted,
                netExecuted
        );
    }

    private void recalculateBudgetTotals(UUID budgetId) {
        List<BudgetItem> items = port.listItemsByBudget(budgetId);
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        for (BudgetItem i : items) {
            if (i.type() == BudgetType.INCOME) {
                income = income.add(i.budgetedAmount());
            } else {
                expense = expense.add(i.budgetedAmount());
            }
        }
        port.updateBudgetTotals(budgetId, income, expense);
    }
}

