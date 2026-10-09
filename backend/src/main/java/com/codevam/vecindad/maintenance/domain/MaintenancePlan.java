package com.codevam.vecindad.maintenance.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MaintenancePlan(
        UUID id,
        UUID assetId,
        String title,
        String description,
        int frequencyDays,
        LocalDate lastPerformedAt,
        LocalDate nextDueDate,
        UUID assignedSupplierId,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {}

