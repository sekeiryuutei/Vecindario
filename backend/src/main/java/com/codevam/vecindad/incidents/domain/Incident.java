package com.codevam.vecindad.incidents.domain;

import java.time.Instant;
import java.util.UUID;

public record Incident(UUID id, IncidentCategory category, String description, String location, Instant occurredAt,
                       UUID reportedBy, UUID assignedTo, IncidentStatus status, String resolution, Instant closedAt,
                       Instant createdAt, Instant updatedAt) {}
