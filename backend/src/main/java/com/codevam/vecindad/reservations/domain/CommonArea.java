package com.codevam.vecindad.reservations.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

public record CommonArea(
        UUID id,
        String name,
        String description,
        int capacity,
        boolean requiresPayment,
        BigDecimal pricePerSlot,
        BigDecimal depositAmount,
        boolean requiresApproval,
        LocalTime openingTime,
        LocalTime closingTime,
        int slotDurationMinutes,
        int maxSlotsPerReservation,
        int maxActiveReservationsPerUnit,
        int minAdvanceHours,
        int maxAdvanceDays,
        int cancellationDeadlineHours,
        String terms,
        LocalTime approvalStartTime,
        LocalTime approvalEndTime,
        boolean isActive,
        Instant createdAt,
        Instant updatedAt
) {
    public CommonArea withId(UUID newId) {
        return new CommonArea(newId, name, description, capacity, requiresPayment, pricePerSlot, depositAmount,
                requiresApproval, openingTime, closingTime, slotDurationMinutes, maxSlotsPerReservation,
                maxActiveReservationsPerUnit, minAdvanceHours, maxAdvanceDays, cancellationDeadlineHours,
                terms, approvalStartTime, approvalEndTime, isActive, createdAt, updatedAt);
    }
}

