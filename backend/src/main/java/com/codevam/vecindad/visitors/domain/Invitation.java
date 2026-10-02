package com.codevam.vecindad.visitors.domain;

import java.time.Instant;
import java.util.UUID;

public record Invitation(UUID id, UUID unitId, String unitIdentifier, String visitorName, String documentNumber,
                         String phone, String plate, int peopleCount, Instant validFrom, Instant validTo,
                         int maxEntries, int usedCount, String status, String notes, Instant createdAt) {}
