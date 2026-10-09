package com.codevam.vecindad.unit;

import com.codevam.vecindad.budget.application.BudgetService;
import com.codevam.vecindad.budget.domain.AnnualBudget;
import com.codevam.vecindad.budget.domain.BudgetStatus;
import com.codevam.vecindad.budget.domain.BudgetSummary;
import com.codevam.vecindad.maintenance.application.MaintenanceService;
import com.codevam.vecindad.maintenance.domain.WorkOrder;
import com.codevam.vecindad.maintenance.domain.WorkOrderPriority;
import com.codevam.vecindad.maintenance.domain.WorkOrderStatus;
import com.codevam.vecindad.maintenance.domain.WorkOrderType;
import com.codevam.vecindad.reports.application.ReportService;
import com.codevam.vecindad.reports.domain.BudgetReport;
import com.codevam.vecindad.reports.domain.MaintenanceReport;
import com.codevam.vecindad.reports.domain.PortfolioReport;
import com.codevam.vecindad.reports.domain.PortfolioReportItem;
import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.tenancy.TenantJdbc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class ReportServiceTest {

    private TenantJdbc tenantJdbc;
    private BudgetService budgetService;
    private MaintenanceService maintenanceService;
    private ReportService reportService;

    @BeforeEach
    void setUp() {
        tenantJdbc = Mockito.mock(TenantJdbc.class);
        budgetService = Mockito.mock(BudgetService.class);
        maintenanceService = Mockito.mock(MaintenanceService.class);
        reportService = new ReportService(tenantJdbc, budgetService, maintenanceService);
    }

    @Test
    void exportPortfolioCsvProducesValidCsvContent() {
        PortfolioReportItem item = new PortfolioReportItem("101", "Torre 1", BigDecimal.valueOf(150000), BigDecimal.valueOf(50000), BigDecimal.ZERO, 1);
        PortfolioReport report = new PortfolioReport(List.of(item), BigDecimal.valueOf(150000), BigDecimal.valueOf(50000), BigDecimal.ZERO, 1);

        byte[] csvBytes = reportService.exportPortfolioCsv(report);
        String csv = new String(csvBytes, StandardCharsets.UTF_8);

        assertTrue(csv.contains("Inmueble;Torre;Saldo Pendiente"));
        assertTrue(csv.contains("101;Torre 1;150000;50000;0;1"));
    }

    @Test
    void exportPortfolioPdfProducesValidPdfDocument() {
        PortfolioReportItem item = new PortfolioReportItem("101", "Torre 1", BigDecimal.valueOf(150000), BigDecimal.valueOf(50000), BigDecimal.ZERO, 1);
        PortfolioReport report = new PortfolioReport(List.of(item), BigDecimal.valueOf(150000), BigDecimal.valueOf(50000), BigDecimal.ZERO, 1);

        byte[] pdfBytes = reportService.exportPortfolioPdf(report);
        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 100);
        String header = new String(pdfBytes, 0, 4, StandardCharsets.US_ASCII);
        assertEquals("%PDF", header);
    }

    @Test
    void getBudgetReportMapsSummaryAccurately() {
        AnnualBudget b = new AnnualBudget(UUID.randomUUID(), 2026, "Presupuesto 2026", BudgetStatus.APPROVED, BigDecimal.valueOf(1000), BigDecimal.valueOf(700), Instant.now(), Instant.now());
        BudgetSummary summary = new BudgetSummary(
                b,
                List.of(),
                BigDecimal.valueOf(1000), BigDecimal.valueOf(800),
                BigDecimal.valueOf(700), BigDecimal.valueOf(600),
                BigDecimal.valueOf(300), BigDecimal.valueOf(200)
        );

        when(budgetService.getSummary(2026)).thenReturn(summary);

        BudgetReport report = reportService.getBudgetReport(2026);
        assertEquals(2026, report.year());
        assertEquals("Presupuesto 2026", report.title());
        assertEquals(BigDecimal.valueOf(1000), report.incomeBudgeted());
        assertEquals(BigDecimal.valueOf(300), report.netBudgeted());
        assertEquals(BigDecimal.valueOf(200), report.netExecuted());
    }

    @Test
    void getMaintenanceReportCalculatesStatsAccurately() {
        when(maintenanceService.listAssets(null, null)).thenReturn(List.of());
        when(maintenanceService.listPlans(null, true)).thenReturn(List.of());

        WorkOrder o1 = new WorkOrder(
                UUID.randomUUID(), "OT-2026-0001", UUID.randomUUID(), null, null,
                WorkOrderType.CORRECTIVE, "Fix 1", "Desc", WorkOrderPriority.HIGH,
                WorkOrderStatus.COMPLETED, BigDecimal.valueOf(100), BigDecimal.valueOf(110),
                UUID.randomUUID(), "John", null, null, Instant.now(), Instant.now(), null, Instant.now(), Instant.now()
        );
        WorkOrder o2 = new WorkOrder(
                UUID.randomUUID(), "OT-2026-0002", UUID.randomUUID(), null, null,
                WorkOrderType.PREVENTIVE, "Fix 2", "Desc", WorkOrderPriority.MEDIUM,
                WorkOrderStatus.IN_PROGRESS, BigDecimal.valueOf(200), BigDecimal.ZERO,
                UUID.randomUUID(), "Mary", null, null, Instant.now(), null, null, Instant.now(), Instant.now()
        );

        when(maintenanceService.searchWorkOrders(null, null, null, null, 0, 1000))
                .thenReturn(PageResult.of(List.of(o1, o2), 0, 1000, 2));

        MaintenanceReport report = reportService.getMaintenanceReport();
        assertEquals(2, report.totalWorkOrders());
        assertEquals(1, report.completedWorkOrders());
        assertEquals(1, report.inProgressWorkOrders());
        assertEquals(0, report.pendingWorkOrders());
        assertEquals(BigDecimal.valueOf(300), report.totalEstimatedCost());
        assertEquals(BigDecimal.valueOf(110), report.totalActualCost());
    }
}

