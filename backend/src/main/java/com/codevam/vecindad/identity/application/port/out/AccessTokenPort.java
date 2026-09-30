package com.codevam.vecindad.identity.application.port.out;

import java.util.UUID;

public interface AccessTokenPort {
    /** Emite un access token. tenantId puede ser null (sesión sin copropiedad activa). */
    String issue(UUID userId, UUID tenantId);
    long expiresInSeconds();
}
