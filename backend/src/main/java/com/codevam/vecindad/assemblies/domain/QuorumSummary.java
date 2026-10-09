package com.codevam.vecindad.assemblies.domain;

import java.math.BigDecimal;

public record QuorumSummary(
        long registeredUnits,
        long totalUnits,
        BigDecimal currentQuorumPercentage,
        BigDecimal requiredQuorumPercentage,
        boolean hasQuorum
) {}

