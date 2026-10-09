package com.codevam.vecindad.suppliers.domain;

import java.time.Instant;
import java.util.UUID;

public record Supplier(
        UUID id,
        String taxId,
        String name,
        String contactName,
        String contactPhone,
        String contactEmail,
        String category,
        Integer rating,
        boolean isActive,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {}

