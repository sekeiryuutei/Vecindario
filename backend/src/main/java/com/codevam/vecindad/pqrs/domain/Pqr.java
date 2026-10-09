package com.codevam.vecindad.pqrs.domain;

import java.time.Instant;
import java.util.UUID;

public record Pqr(
        UUID id,
        String ticketNumber,
        UUID unitId,
        UUID personId,
        UUID userId,
        PqrCategory category,
        PqrPriority priority,
        PqrStatus status,
        String title,
        String description,
        boolean isAnonymous,
        UUID assignedTo,
        int slaTargetHours,
        Instant slaDueAt,
        Instant resolvedAt,
        Instant closedAt,
        String resolutionNotes,
        Instant createdAt,
        Instant updatedAt
) {
    public boolean isOverdue() {
        if (status == PqrStatus.CLOSED || status == PqrStatus.RESOLVED || status == PqrStatus.REJECTED) {
            return false;
        }
        return slaDueAt != null && Instant.now().isAfter(slaDueAt);
    }
}

