package com.codevam.vecindad.reports.domain;

import java.math.BigDecimal;
import java.util.List;

public record PortfolioReport(
        List<PortfolioReportItem> items,
        BigDecimal totalDueSum,
        BigDecimal totalOverdueSum,
        BigDecimal totalCreditSum,
        int totalUnitsWithDebt
) {}

