package com.codevam.vecindad.communications.domain;

import java.time.Instant;
import java.util.UUID;

public record Communication(
        UUID id,
        String title,
        String summary,
        String content,
        CommunicationPriority priority,
        TargetAudience targetAudience,
        String targetTower,
        CommunicationStatus status,
        boolean isPinned,
        UUID createdBy,
        Instant publishedAt,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt
) {
    public Communication withId(UUID newId) {
        return new Communication(newId, title, summary, content, priority, targetAudience, targetTower,
                status, isPinned, createdBy, publishedAt, expiresAt, createdAt, updatedAt);
    }
}

