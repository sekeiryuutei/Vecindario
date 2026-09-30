package com.codevam.vecindad.properties.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PropertyUnit(UUID id, UnitType type, String identifier, String unitNumber, String tower,
                           Integer floorNumber, BigDecimal coefficient, BigDecimal areaM2, UnitStatus status,
                           Instant createdAt, Instant updatedAt, Instant deletedAt, UUID deletedBy) {}
