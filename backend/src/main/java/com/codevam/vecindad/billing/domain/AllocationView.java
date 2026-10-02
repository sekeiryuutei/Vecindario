package com.codevam.vecindad.billing.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AllocationView(UUID chargeId, ConceptType conceptType, String chargeDescription, BigDecimal amount, Instant reversedAt) {}
