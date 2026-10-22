package com.codevam.vecindad.training.domain;

import java.time.Instant;
import java.util.UUID;

public record TrainingEnrollment(UUID id, UUID sessionId, UUID personId, String personName,
                                 UUID unitId, String unitIdentifier, EnrollmentStatus status,
                                 Instant createdAt, Instant updatedAt) {}
