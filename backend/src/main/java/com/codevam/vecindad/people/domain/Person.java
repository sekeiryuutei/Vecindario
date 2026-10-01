package com.codevam.vecindad.people.domain;

import java.time.Instant;
import java.util.UUID;

public record Person(UUID id, String documentType, String documentNumber, String fullName, String email,
                     String phone, UUID userId, Instant createdAt, Instant updatedAt) {}
