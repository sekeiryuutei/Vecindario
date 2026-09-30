package com.codevam.vecindad.identity.domain;

import java.time.Instant;
import java.util.UUID;

public record Membership(UUID id, UUID userId, UUID tenantId, String roleCode, MembershipStatus status,
                         Instant grantedAt, Instant revokedAt, Instant lastActivityAt) {}
