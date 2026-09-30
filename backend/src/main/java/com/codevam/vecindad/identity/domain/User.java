package com.codevam.vecindad.identity.domain;

import java.time.Instant;
import java.util.UUID;

public record User(UUID id, String email, String fullName, String passwordHash, String platformRole,
                   UserStatus status, int failedAttempts, Instant lockedUntil, UUID lastTenantId) {

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }
}
