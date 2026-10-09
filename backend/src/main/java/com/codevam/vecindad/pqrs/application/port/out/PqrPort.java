package com.codevam.vecindad.pqrs.application.port.out;

import com.codevam.vecindad.pqrs.domain.*;
import com.codevam.vecindad.shared.model.PageResult;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PqrPort {
    String nextTicketNumber();
    Pqr insert(Pqr pqr);
    Optional<Pqr> findById(UUID id);
    PageResult<Pqr> search(PqrCategory category, PqrStatus status, PqrPriority priority,
                           Boolean overdue, int page, int size);
    PageResult<Pqr> findByUser(UUID userId, int page, int size);
    boolean updateStatus(UUID id, PqrStatus status, String resolutionNotes, Instant resolvedAt, Instant closedAt);
    boolean assign(UUID id, UUID assignedUserId);
    PqrMessage addMessage(PqrMessage message);
    List<PqrMessage> listMessages(UUID pqrId, boolean includeInternal);
    PqrSlaMetrics getSlaMetrics();
}

