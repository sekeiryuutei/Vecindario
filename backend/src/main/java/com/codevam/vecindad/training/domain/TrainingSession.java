package com.codevam.vecindad.training.domain;

import java.time.Instant;
import java.util.UUID;

public record TrainingSession(UUID id, String title, String description, String trainerName,
                              TrainingModality modality, String location, String meetingLink,
                              Instant startsAt, Instant endsAt, int capacity, TrainingStatus status,
                              int enrolledCount, Instant createdAt, Instant updatedAt) {}
