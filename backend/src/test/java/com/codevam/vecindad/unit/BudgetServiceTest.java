package com.codevam.vecindad.unit;

import com.codevam.vecindad.budget.application.BudgetService;
import com.codevam.vecindad.budget.application.port.out.BudgetPort;
import com.codevam.vecindad.budget.domain.*;
import com.codevam.vecindad.shared.error.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BudgetServiceTest {

    private BudgetPort port;
    private BudgetService service;

    @BeforeEach
    void setUp() {
        port = Mockito.mock(BudgetPort.class);
        service = new BudgetService(port);
    }

    @Test
    void createBudgetSuccessfully() {
        BudgetService.CreateBudgetCommand cmd = new BudgetService.CreateBudgetCommand(2026, "Presupuesto 2026");
        when(port.findBudgetByYear(2026)).thenReturn(Optional.empty());
        when(port.insertBudget(any())).thenAnswer(inv -> inv.getArgument(0));

        AnnualBudget created = service.createBudget(cmd);
        assertNotNull(created);
        assertEquals(2026, created.year());
    }

    @Test
    void createDuplicateBudgetThrowsError() {
        AnnualBudget existing = new AnnualBudget(UUID.randomUUID(), 2026, "Existente", BudgetStatus.APPROVED, BigDecimal.ZERO, BigDecimal.ZERO, Instant.now(), Instant.now());
        when(port.findBudgetByYear(2026)).thenReturn(Optional.of(existing));

        BudgetService.CreateBudgetCommand cmd = new BudgetService.CreateBudgetCommand(2026, "Nuevo");
        assertThrows(ApiException.class, () -> service.createBudget(cmd));
    }

    @Test
    void budgetSummaryCalculatesTotalsCorrectly() {
        UUID budgetId = UUID.randomUUID();
        AnnualBudget budget = new AnnualBudget(budgetId, 2026, "Presupuesto 2026", BudgetStatus.APPROVED, BigDecimal.ZERO, BigDecimal.ZERO, Instant.now(), Instant.now());
        when(port.findBudgetByYear(2026)).thenReturn(Optional.of(budget));

        UUID incId = UUID.randomUUID();
        UUID expId = UUID.randomUUID();

        BudgetItem incItem = new BudgetItem(incId, budgetId, "1.1", "Ingresos Cuotas", BudgetType.INCOME, BigDecimal.valueOf(1000), BigDecimal.valueOf(800), Instant.now(), Instant.now());
        BudgetItem expItem = new BudgetItem(expId, budgetId, "2.1", "Gastos Aseo", BudgetType.EXPENSE, BigDecimal.valueOf(600), BigDecimal.valueOf(500), Instant.now(), Instant.now());

        when(port.listItemsByBudget(budgetId)).thenReturn(List.of(incItem, expItem));

        BudgetSummary summary = service.getSummary(2026);

        assertEquals(BigDecimal.valueOf(1000), summary.totalIncomeBudgeted());
        assertEquals(BigDecimal.valueOf(800), summary.totalIncomeExecuted());
        assertEquals(BigDecimal.valueOf(600), summary.totalExpenseBudgeted());
        assertEquals(BigDecimal.valueOf(500), summary.totalExpenseExecuted());
        assertEquals(BigDecimal.valueOf(400), summary.netBudgeted()); // 1000 - 600
        assertEquals(BigDecimal.valueOf(300), summary.netExecuted()); // 800 - 500
    }
}

