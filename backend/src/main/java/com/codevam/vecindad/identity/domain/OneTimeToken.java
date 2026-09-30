package com.codevam.vecindad.identity.domain;

import java.time.Instant;
import java.util.UUID;

public record OneTimeToken(UUID id, UUID userId, String purpose, Instant expiresAt) {}
