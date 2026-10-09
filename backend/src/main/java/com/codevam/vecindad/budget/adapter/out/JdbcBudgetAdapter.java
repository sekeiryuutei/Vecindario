package com.codevam.vecindad.budget.adapter.out;

import com.codevam.vecindad.budget.application.port.out.BudgetPort;
import com.codevam.vecindad.budget.domain.*;
import com.codevam.vecindad.shared.persistence.Jdbc;
import com.codevam.vecindad.shared.tenancy.TenantJdbc;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcBudgetAdapter implements BudgetPort {

    private static final String BUDGET_COLS = "id, year, title, status, total_income_budgeted, total_expense_budgeted, created_at, updated_at";
    private static final String ITEM_COLS = "id, budget_id, code, name, type, budgeted_amount, executed_amount, created_at, updated_at";
    private static final String EXEC_COLS = "id, budget_item_id, work_order_id, supplier_id, amount, execution_date, invoice_number, description, registered_by, created_at";

    private final RowMapper<AnnualBudget> budgetMapper = (rs, rowNum) -> new AnnualBudget(
            Jdbc.uuid(rs, "id"),
            rs.getInt("year"),
            rs.getString("title"),
            BudgetStatus.valueOf(rs.getString("status")),
            rs.getBigDecimal("total_income_budgeted"),
            rs.getBigDecimal("total_expense_budgeted"),
            Jdbc.instant(rs, "created_at"),
            Jdbc.instant(rs, "updated_at")
    );

    private final RowMapper<BudgetItem> itemMapper = (rs, rowNum) -> new BudgetItem(
            Jdbc.uuid(rs, "id"),
            Jdbc.uuid(rs, "budget_id"),
            rs.getString("code"),
            rs.getString("name"),
            BudgetType.valueOf(rs.getString("type")),
            rs.getBigDecimal("budgeted_amount"),
            rs.getBigDecimal("executed_amount"),
            Jdbc.instant(rs, "created_at"),
            Jdbc.instant(rs, "updated_at")
    );

    private final RowMapper<BudgetExecution> execMapper = (rs, rowNum) -> new BudgetExecution(
            Jdbc.uuid(rs, "id"),
            Jdbc.uuid(rs, "budget_item_id"),
            Jdbc.uuid(rs, "work_order_id"),
            Jdbc.uuid(rs, "supplier_id"),
            rs.getBigDecimal("amount"),
            rs.getDate("execution_date").toLocalDate(),
            rs.getString("invoice_number"),
            rs.getString("description"),
            Jdbc.uuid(rs, "registered_by"),
            Jdbc.instant(rs, "created_at")
    );

    private final TenantJdbc t;

    public JdbcBudgetAdapter(TenantJdbc t) {
        this.t = t;
    }

    // --- Annual Budget ---
    @Override
    public AnnualBudget insertBudget(AnnualBudget b) {
        UUID id = b.id() == null ? UUID.randomUUID() : b.id();
        String sql = """
                INSERT INTO {s}.annual_budgets (
                    id, year, title, status, total_income_budgeted, total_expense_budgeted
                ) VALUES (?, ?, ?, ?, ?, ?)
                """;
        t.jdbc().update(t.q(sql),
                id, b.year(), b.title(), b.status().name(),
                b.totalIncomeBudgeted(), b.totalExpenseBudgeted());
        return findBudgetById(id).orElseThrow();
    }

    @Override
    public Optional<AnnualBudget> findBudgetById(UUID id) {
        return t.jdbc().query(t.q("SELECT " + BUDGET_COLS + " FROM {s}.annual_budgets WHERE id = ?"),
                budgetMapper, id).stream().findFirst();
    }

    @Override
    public Optional<AnnualBudget> findBudgetByYear(int year) {
        return t.jdbc().query(t.q("SELECT " + BUDGET_COLS + " FROM {s}.annual_budgets WHERE year = ?"),
                budgetMapper, year).stream().findFirst();
    }

    @Override
    public List<AnnualBudget> listBudgets() {
        return t.jdbc().query(t.q("SELECT " + BUDGET_COLS + " FROM {s}.annual_budgets ORDER BY year DESC"),
                budgetMapper);
    }

    @Override
    public boolean updateBudgetTotals(UUID budgetId, BigDecimal incomeBudgeted, BigDecimal expenseBudgeted) {
        return t.jdbc().update(t.q("UPDATE {s}.annual_budgets SET total_income_budgeted = ?, total_expense_budgeted = ?, updated_at = now() WHERE id = ?"),
                incomeBudgeted, expenseBudgeted, budgetId) > 0;
    }

    // --- Budget Items ---
    @Override
    public BudgetItem insertItem(BudgetItem item) {
        UUID id = item.id() == null ? UUID.randomUUID() : item.id();
        String sql = """
                INSERT INTO {s}.budget_items (
                    id, budget_id, code, name, type, budgeted_amount, executed_amount
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """;
        t.jdbc().update(t.q(sql),
                id, item.budgetId(), item.code(), item.name(), item.type().name(),
                item.budgetedAmount(), item.executedAmount());
        return findItemById(id).orElseThrow();
    }

    @Override
    public Optional<BudgetItem> findItemById(UUID id) {
        return t.jdbc().query(t.q("SELECT " + ITEM_COLS + " FROM {s}.budget_items WHERE id = ?"),
                itemMapper, id).stream().findFirst();
    }

    @Override
    public List<BudgetItem> listItemsByBudget(UUID budgetId) {
        return t.jdbc().query(t.q("SELECT " + ITEM_COLS + " FROM {s}.budget_items WHERE budget_id = ? ORDER BY code ASC"),
                itemMapper, budgetId);
    }

    @Override
    public boolean updateItemExecutedAmount(UUID itemId, BigDecimal executedAmount) {
        return t.jdbc().update(t.q("UPDATE {s}.budget_items SET executed_amount = ?, updated_at = now() WHERE id = ?"),
                executedAmount, itemId) > 0;
    }

    // --- Budget Executions ---
    @Override
    public BudgetExecution insertExecution(BudgetExecution e) {
        UUID id = e.id() == null ? UUID.randomUUID() : e.id();
        String sql = """
                INSERT INTO {s}.budget_executions (
                    id, budget_item_id, work_order_id, supplier_id, amount,
                    execution_date, invoice_number, description, registered_by
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        t.jdbc().update(t.q(sql),
                id, e.budgetItemId(), e.workOrderId(), e.supplierId(), e.amount(),
                Date.valueOf(e.executionDate()), e.invoiceNumber(), e.description(), e.registeredBy());
        return t.jdbc().query(t.q("SELECT " + EXEC_COLS + " FROM {s}.budget_executions WHERE id = ?"),
                execMapper, id).stream().findFirst().orElseThrow();
    }

    @Override
    public List<BudgetExecution> listExecutionsByItem(UUID itemId) {
        return t.jdbc().query(t.q("SELECT " + EXEC_COLS + " FROM {s}.budget_executions WHERE budget_item_id = ? ORDER BY execution_date DESC"),
                execMapper, itemId);
    }

    @Override
    public List<BudgetExecution> listExecutionsByBudget(UUID budgetId) {
        String sql = """
                SELECT e.* FROM {s}.budget_executions e
                JOIN {s}.budget_items i ON i.id = e.budget_item_id
                WHERE i.budget_id = ?
                ORDER BY e.execution_date DESC
                """;
        return t.jdbc().query(t.q(sql), execMapper, budgetId);
    }
}

