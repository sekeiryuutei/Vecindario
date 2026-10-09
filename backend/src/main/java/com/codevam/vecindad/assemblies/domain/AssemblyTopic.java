package com.codevam.vecindad.assemblies.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AssemblyTopic(
        UUID id,
        UUID assemblyId,
        int orderNum,
        String title,
        String description,
        VotingType votingType,
        TopicStatus status,
        boolean requiresQualifiedMajority,
        List<String> options,
        Instant createdAt
) {}

