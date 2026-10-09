package com.codevam.vecindad.reports.application;

import com.codevam.vecindad.budget.application.BudgetService;
import com.codevam.vecindad.budget.domain.BudgetSummary;
import com.codevam.vecindad.maintenance.application.MaintenanceService;
import com.codevam.vecindad.maintenance.domain.AssetStatus;
import com.codevam.vecindad.maintenance.domain.WorkOrderStatus;
import com.codevam.vecindad.reports.domain.*;
import com.codevam.vecindad.shared.tenancy.TenantJdbc;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

@Service
public class ReportService {

    private final TenantJdbc t;
    private final BudgetService budgetService;
    private final MaintenanceService maintenanceService;

    public ReportService(TenantJdbc t, BudgetService budgetService, MaintenanceService maintenanceService) {
        this.t = t;
        this.budgetService = budgetService;
        this.maintenanceService = maintenanceService;
    }

    public PortfolioReport getPortfolioReport() {
        String sql = """
                SELECT
                    u.identifier AS unit_identifier,
                    COALESCE(u.tower, '') AS tower,
                    COALESCE(SUM(CASE WHEN c.voided_at IS NULL THEN (c.amount + c.adjusted_amount - c.paid_amount) ELSE 0 END), 0) AS total_due,
                    COALESCE(SUM(CASE WHEN c.voided_at IS NULL AND c.due_date < CURRENT_DATE THEN (c.amount + c.adjusted_amount - c.paid_amount) ELSE 0 END), 0) AS overdue_amount,
                    COALESCE((SELECT SUM(unapplied_amount) FROM {s}.payments p WHERE p.unit_id = u.id), 0) AS credit_balance,
                    COALESCE(COUNT(CASE WHEN c.voided_at IS NULL AND c.due_date < CURRENT_DATE AND (c.amount + c.adjusted_amount > c.paid_amount) THEN 1 END), 0) AS overdue_count
                FROM {s}.property_units u
                LEFT JOIN {s}.charges c ON c.unit_id = u.id
                WHERE u.status = 'ACTIVE' AND u.deleted_at IS NULL
                GROUP BY u.id, u.identifier, u.tower
                ORDER BY overdue_amount DESC, u.identifier ASC
                """;

        List<PortfolioReportItem> items = t.jdbc().query(t.q(sql), (rs, rowNum) -> new PortfolioReportItem(
                rs.getString("unit_identifier"),
                rs.getString("tower"),
                rs.getBigDecimal("total_due"),
                rs.getBigDecimal("overdue_amount"),
                rs.getBigDecimal("credit_balance"),
                rs.getInt("overdue_count")
        ));

        BigDecimal totalDue = BigDecimal.ZERO;
        BigDecimal totalOverdue = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        int unitsWithDebt = 0;

        for (PortfolioReportItem i : items) {
            totalDue = totalDue.add(i.totalDue());
            totalOverdue = totalOverdue.add(i.overdueAmount());
            totalCredit = totalCredit.add(i.creditBalance());
            if (i.totalDue().compareTo(BigDecimal.ZERO) > 0) {
                unitsWithDebt++;
            }
        }

        return new PortfolioReport(items, totalDue, totalOverdue, totalCredit, unitsWithDebt);
    }

    public byte[] exportPortfolioCsv(PortfolioReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("Inmueble;Torre;Saldo Pendiente;Saldo en Mora;Saldo a Favor;Cuotas Vencidas\n");
        for (PortfolioReportItem item : report.items()) {
            sb.append(item.unitIdentifier()).append(";")
              .append(item.tower()).append(";")
              .append(item.totalDue()).append(";")
              .append(item.overdueAmount()).append(";")
              .append(item.creditBalance()).append(";")
              .append(item.overdueChargesCount()).append("\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] exportPortfolioPdf(PortfolioReport report) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            com.lowagie.text.Document doc = new com.lowagie.text.Document(PageSize.A4, 36, 36, 54, 54);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 9);
            Font summaryFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);

            Paragraph p = new Paragraph("INFORME CONSOLIDADO DE CARTERA Y MOROSIDAD", titleFont);
            p.setAlignment(Element.ALIGN_CENTER);
            p.setSpacingAfter(15);
            doc.add(p);

            Paragraph meta = new Paragraph("Fecha de generación: " + LocalDate.now() + " | Inmuebles con deuda: " + report.totalUnitsWithDebt(), bodyFont);
            meta.setSpacingAfter(15);
            doc.add(meta);

            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{2f, 1.5f, 2.5f, 2.5f, 2.5f, 1.5f});

            addCell(table, "Inmueble", headerFont, true);
            addCell(table, "Torre", headerFont, true);
            addCell(table, "Saldo Pendiente", headerFont, true);
            addCell(table, "Saldo en Mora", headerFont, true);
            addCell(table, "Saldo a Favor", headerFont, true);
            addCell(table, "Cuotas Venc.", headerFont, true);

            for (PortfolioReportItem item : report.items()) {
                addCell(table, item.unitIdentifier(), bodyFont, false);
                addCell(table, item.tower(), bodyFont, false);
                addCell(table, "$" + item.totalDue().toPlainString(), bodyFont, false);
                addCell(table, "$" + item.overdueAmount().toPlainString(), bodyFont, false);
                addCell(table, "$" + item.creditBalance().toPlainString(), bodyFont, false);
                addCell(table, String.valueOf(item.overdueChargesCount()), bodyFont, false);
            }
            doc.add(table);

            Paragraph totals = new Paragraph("\nTOTALES CONSOLIDADOS:\n"
                    + "Total Pendiente: $" + report.totalDueSum().toPlainString() + "\n"
                    + "Total en Mora: $" + report.totalOverdueSum().toPlainString() + "\n"
                    + "Total Saldo a Favor: $" + report.totalCreditSum().toPlainString(), summaryFont);
            totals.setSpacingBefore(15);
            doc.add(totals);

            doc.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error generando PDF de cartera", e);
        }
    }

    private void addCell(PdfPTable table, String text, Font font, boolean isHeader) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(5);
        if (isHeader) {
            cell.setBackgroundColor(new java.awt.Color(230, 235, 245));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        }
        table.addCell(cell);
    }

    public BudgetReport getBudgetReport(int year) {
        BudgetSummary s = budgetService.getSummary(year);
        return new BudgetReport(
                s.budget().year(),
                s.budget().title(),
                s.totalIncomeBudgeted(),
                s.totalIncomeExecuted(),
                s.totalExpenseBudgeted(),
                s.totalExpenseExecuted(),
                s.netBudgeted(),
                s.netExecuted()
        );
    }

    public MaintenanceReport getMaintenanceReport() {
        int totalAssets = maintenanceService.listAssets(null, null).size();
        int activePlans = maintenanceService.listPlans(null, true).size();
        var orders = maintenanceService.searchWorkOrders(null, null, null, null, 0, 1000).content();

        int totalOrders = orders.size();
        int completed = 0;
        int inProgress = 0;
        int pending = 0;
        BigDecimal estCost = BigDecimal.ZERO;
        BigDecimal actCost = BigDecimal.ZERO;

        for (var o : orders) {
            if (o.status() == WorkOrderStatus.COMPLETED) completed++;
            else if (o.status() == WorkOrderStatus.IN_PROGRESS) inProgress++;
            else if (o.status() == WorkOrderStatus.PENDING || o.status() == WorkOrderStatus.ASSIGNED) pending++;

            estCost = estCost.add(o.estimatedCost());
            actCost = actCost.add(o.actualCost());
        }

        return new MaintenanceReport(
                totalAssets, activePlans, totalOrders, completed, inProgress, pending, estCost, actCost
        );
    }
}

