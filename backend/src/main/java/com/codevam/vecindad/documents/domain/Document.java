package com.codevam.vecindad.documents.domain;

import java.time.Instant;
import java.util.UUID;

public record Document(
        UUID id,
        String title,
        String description,
        DocumentCategory category,
        UUID unitId,
        String storageKey,
        String fileName,
        long fileSize,
        String contentType,
        String minimumRole,
        UUID uploadedBy,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {}

