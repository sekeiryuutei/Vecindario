package com.codevam.vecindad.billing.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record BillableUnit(UUID id, String identifier, BigDecimal coefficient) {}
