package com.codevam.vecindad.reports.domain;

import java.math.BigDecimal;

public record PortfolioReportItem(
        String unitIdentifier,
        String tower,
        BigDecimal totalDue,
        BigDecimal overdueAmount,
        BigDecimal creditBalance,
        int overdueChargesCount
) {}

