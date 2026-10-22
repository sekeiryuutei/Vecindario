package com.codevam.vecindad.appointments.domain;

import java.time.Instant;
import java.util.UUID;

public record Appointment(UUID id, UUID unitId, String unitIdentifier, UUID personId, String personName,
                          String subject, String reason, Instant scheduledAt, int durationMinutes,
                          AppointmentModality modality, String location, String meetingLink,
                          AppointmentStatus status, String adminNotes, String cancellationReason,
                          Instant createdAt, Instant updatedAt) {}
