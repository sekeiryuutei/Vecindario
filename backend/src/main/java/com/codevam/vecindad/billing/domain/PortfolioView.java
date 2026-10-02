package com.codevam.vecindad.billing.domain;

import com.codevam.vecindad.shared.model.PageResult;

import java.math.BigDecimal;

public record PortfolioView(BigDecimal totalOutstanding, BigDecimal totalOverdue, BigDecimal totalCredit, PageResult<PortfolioRow> units) {}
