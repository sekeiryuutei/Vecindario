package com.codevam.vecindad.assemblies.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AssemblyVote(
        UUID id,
        UUID topicId,
        UUID unitId,
        UUID personId,
        String choice,
        BigDecimal weight,
        Instant votedAt
) {}

