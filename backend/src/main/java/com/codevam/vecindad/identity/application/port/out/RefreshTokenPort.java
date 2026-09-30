package com.codevam.vecindad.identity.application.port.out;

import com.codevam.vecindad.identity.domain.RefreshToken;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenPort {
    UUID save(UUID userId, String tokenHash, Instant expiresAt, String ip, String userAgent);
    Optional<RefreshToken> findByHash(String tokenHash);
    void revoke(UUID id, UUID replacedBy, Instant at);
    void revokeAllForUser(UUID userId, Instant at);
}
