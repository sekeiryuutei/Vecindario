package com.codevam.vecindad.reports.infrastructure.web;

import com.codevam.vecindad.reports.application.ReportService;
import com.codevam.vecindad.reports.domain.BudgetReport;
import com.codevam.vecindad.reports.domain.MaintenanceReport;
import com.codevam.vecindad.reports.domain.PortfolioReport;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/portfolio")
    @PreAuthorize("hasAuthority('REPORTS_VIEW')")
    public ResponseEntity<PortfolioReport> getPortfolioReport() {
        return ResponseEntity.ok(reportService.getPortfolioReport());
    }

    @GetMapping("/portfolio/export/csv")
    @PreAuthorize("hasAuthority('REPORTS_EXPORT')")
    public ResponseEntity<byte[]> exportPortfolioCsv() {
        PortfolioReport report = reportService.getPortfolioReport();
        byte[] csv = reportService.exportPortfolioCsv(report);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"reporte-cartera.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv);
    }

    @GetMapping("/portfolio/export/pdf")
    @PreAuthorize("hasAuthority('REPORTS_EXPORT')")
    public ResponseEntity<byte[]> exportPortfolioPdf() {
        PortfolioReport report = reportService.getPortfolioReport();
        byte[] pdf = reportService.exportPortfolioPdf(report);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"reporte-cartera.pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/budget/{year}")
    @PreAuthorize("hasAuthority('REPORTS_VIEW')")
    public ResponseEntity<BudgetReport> getBudgetReport(@PathVariable int year) {
        return ResponseEntity.ok(reportService.getBudgetReport(year));
    }

    @GetMapping("/maintenance")
    @PreAuthorize("hasAuthority('REPORTS_VIEW')")
    public ResponseEntity<MaintenanceReport> getMaintenanceReport() {
        return ResponseEntity.ok(reportService.getMaintenanceReport());
    }
}

