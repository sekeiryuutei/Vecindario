package com.codevam.vecindad.reports.domain;

import java.math.BigDecimal;

public record MaintenanceReport(
        int totalAssets,
        int activePlans,
        int totalWorkOrders,
        int completedWorkOrders,
        int inProgressWorkOrders,
        int pendingWorkOrders,
        BigDecimal totalEstimatedCost,
        BigDecimal totalActualCost
) {}

