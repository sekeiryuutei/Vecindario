package com.codevam.vecindad.pqrs.domain;

import java.time.Instant;
import java.util.UUID;

public record PqrMessage(
        UUID id,
        UUID pqrId,
        UUID senderUserId,
        String senderName,
        boolean isInternal,
        String message,
        Instant createdAt
) {}

