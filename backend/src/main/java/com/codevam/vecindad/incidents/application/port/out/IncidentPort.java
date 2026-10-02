package com.codevam.vecindad.incidents.application.port.out;

import com.codevam.vecindad.incidents.domain.Incident;
import com.codevam.vecindad.incidents.domain.IncidentCategory;
import com.codevam.vecindad.incidents.domain.IncidentStatus;
import com.codevam.vecindad.shared.model.PageResult;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface IncidentPort {
    Incident insert(Incident i);
    Optional<Incident> findById(UUID id);
    PageResult<Incident> search(IncidentCategory category, IncidentStatus status, Instant from, Instant to, int page, int size);
    boolean updateStatus(UUID id, IncidentStatus status, String resolution);
    boolean assign(UUID id, UUID userId);
}
