package com.codevam.vecindad.reservations.application.port.out;

import com.codevam.vecindad.reservations.domain.Reservation;
import com.codevam.vecindad.reservations.domain.ReservationStatus;
import com.codevam.vecindad.shared.model.PageResult;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReservationPort {
    Reservation insert(Reservation r);
    Optional<Reservation> findById(UUID id);
    List<Reservation> findOverlapping(UUID commonAreaId, Instant start, Instant end, UUID excludeId);
    int countActiveByUnit(UUID unitId);
    List<Reservation> findByUnit(UUID unitId);
    List<Reservation> findByCommonAreaAndRange(UUID commonAreaId, Instant start, Instant end);
    PageResult<Reservation> search(UUID commonAreaId, UUID unitId, ReservationStatus status,
                                   Instant from, Instant to, int page, int size);
    boolean updateStatus(UUID id, ReservationStatus status, String rejectionReason, String cancellationReason);
    boolean isUnitBlockedByDebt(UUID unitId);
}

