package com.codevam.vecindad.vehicles.domain;

import java.time.Instant;
import java.util.UUID;

public record AccessEvent(UUID id, UUID vehicleId, UUID unitId, String plate, String eventType, Instant occurredAt,
                          UUID guardUserId, String cameraId, String source, String method, UUID entryEventId) {}
