package com.codevam.vecindad.reservations.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record Reservation(
        UUID id,
        UUID commonAreaId,
        UUID unitId,
        UUID personId,
        UUID createdBy,
        Instant startTime,
        Instant endTime,
        ReservationStatus status,
        BigDecimal totalPrice,
        BigDecimal depositAmount,
        PaymentMethod paymentMethod,
        String paymentReference,
        String rejectionReason,
        String cancellationReason,
        String notes,
        Instant createdAt,
        Instant updatedAt
) {
    public Reservation withId(UUID newId) {
        return new Reservation(newId, commonAreaId, unitId, personId, createdBy, startTime, endTime,
                status, totalPrice, depositAmount, paymentMethod, paymentReference, rejectionReason,
                cancellationReason, notes, createdAt, updatedAt);
    }
}

