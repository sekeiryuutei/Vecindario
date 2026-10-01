package com.codevam.vecindad.vehicles.domain;

import java.time.Instant;
import java.util.UUID;

public record SecurityAlert(UUID id, String alertType, UUID vehicleId, String plate, UUID unitId, String message,
                            Instant createdAt, Instant resolvedAt) {}
