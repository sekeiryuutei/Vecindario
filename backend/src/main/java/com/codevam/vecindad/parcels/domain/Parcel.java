package com.codevam.vecindad.parcels.domain;

import java.time.Instant;
import java.util.UUID;

public record Parcel(UUID id, UUID unitId, String unitIdentifier, String recipientName, String carrier,
                     String trackingNumber, String description, String status, Instant receivedAt, Instant notifiedAt,
                     Instant deliveredAt, String deliveredTo, Instant returnedAt, String note) {}
