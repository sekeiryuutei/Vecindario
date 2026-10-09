package com.codevam.vecindad.assemblies.domain;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record TopicResults(
        UUID topicId,
        String title,
        VotingType votingType,
        TopicStatus status,
        long totalVotes,
        BigDecimal totalWeight,
        Map<String, BigDecimal> voteTallies,
        Map<String, BigDecimal> percentageTallies,
        String winningOption
) {}

