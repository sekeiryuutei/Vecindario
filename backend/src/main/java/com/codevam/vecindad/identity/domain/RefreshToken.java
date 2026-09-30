package com.codevam.vecindad.identity.domain;

import java.time.Instant;
import java.util.UUID;

public record RefreshToken(UUID id, UUID userId, Instant expiresAt, Instant revokedAt) {}
