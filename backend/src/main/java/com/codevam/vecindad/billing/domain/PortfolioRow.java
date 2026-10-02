package com.codevam.vecindad.billing.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PortfolioRow(UUID unitId, String unitIdentifier, BigDecimal outstanding, BigDecimal overdue,
                           BigDecimal creditBalance, LocalDate oldestDueDate) {}
