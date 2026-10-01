package com.codevam.vecindad.vehicles.application.port.out;

import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.vehicles.domain.AccessEvent;
import com.codevam.vecindad.vehicles.domain.SecurityAlert;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface AccessPort {
    /** Atómico: marca INSIDE y registra el evento en una sola sentencia. Vacío si no cumple (ya dentro, bloqueado...). */
    Optional<AccessEvent> registerEntry(UUID vehicleId, UUID guard, String source, String method, String cameraId);
    /** Atómico: marca OUTSIDE, registra el evento y lo enlaza con su entrada. Vacío si no estaba dentro. */
    Optional<AccessEvent> registerExit(UUID vehicleId, UUID guard, String source, String method, String cameraId);
    PageResult<AccessEvent> events(String plate, UUID unitId, Instant from, Instant to, int page, int size);

    void saveAlert(String type, UUID vehicleId, String plate, UUID unitId, String message, UUID guard);
    PageResult<SecurityAlert> alerts(boolean onlyOpen, int page, int size);
    boolean resolveAlert(UUID id, UUID by);
}
