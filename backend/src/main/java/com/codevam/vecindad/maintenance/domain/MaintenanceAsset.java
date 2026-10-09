package com.codevam.vecindad.maintenance.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record MaintenanceAsset(
        UUID id,
        String name,
        String category,
        String location,
        String serialNumber,
        String brandModel,
        LocalDate installationDate,
        LocalDate warrantyExpiration,
        AssetStatus status,
        Instant createdAt,
        Instant updatedAt
) {}

