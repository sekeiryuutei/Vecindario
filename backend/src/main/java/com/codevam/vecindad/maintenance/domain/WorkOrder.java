package com.codevam.vecindad.maintenance.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record WorkOrder(
        UUID id,
        String orderNumber,
        UUID assetId,
        UUID planId,
        UUID supplierId,
        WorkOrderType type,
        String title,
        String description,
        WorkOrderPriority priority,
        WorkOrderStatus status,
        BigDecimal estimatedCost,
        BigDecimal actualCost,
        UUID createdBy,
        String assignedTo,
        Instant scheduledStart,
        Instant scheduledEnd,
        Instant startedAt,
        Instant completedAt,
        String completionNotes,
        Instant createdAt,
        Instant updatedAt
) {}

