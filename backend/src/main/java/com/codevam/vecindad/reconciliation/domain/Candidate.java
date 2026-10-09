package com.codevam.vecindad.reconciliation.domain;

import java.math.BigDecimal;
import java.util.UUID;

/** score entre 0 y 1 (p. ej. 0,940 = 94 %). signals explica por qué. */
public record Candidate(UUID unitId, String unitIdentifier, BigDecimal score, String signals) {}
