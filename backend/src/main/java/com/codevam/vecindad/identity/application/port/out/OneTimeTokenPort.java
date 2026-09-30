package com.codevam.vecindad.identity.application.port.out;

import com.codevam.vecindad.identity.domain.OneTimeToken;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OneTimeTokenPort {
    void create(UUID userId, String purpose, String tokenHash, Instant expiresAt);
    Optional<OneTimeToken> findUsable(String tokenHash, Instant now);
    void markUsed(UUID id, Instant at);
}
