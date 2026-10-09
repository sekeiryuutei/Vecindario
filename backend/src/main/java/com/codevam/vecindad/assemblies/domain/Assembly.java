package com.codevam.vecindad.assemblies.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Assembly(
        UUID id,
        String title,
        AssemblyType type,
        Instant scheduledAt,
        AssemblyStatus status,
        String location,
        boolean isVirtual,
        String meetingLink,
        BigDecimal requiredQuorumPercentage,
        Instant createdAt,
        Instant updatedAt
) {}

