package com.codevam.vecindad.vehicles.application.port.out;

import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.vehicles.domain.Presence;
import com.codevam.vecindad.vehicles.domain.Vehicle;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehiclePort {
    Optional<Vehicle> findById(UUID id);
    Optional<Vehicle> findByPlate(String normalizedPlate);
    PageResult<Vehicle> search(String plate, UUID unitId, Presence presence, int page, int size);
    List<Vehicle> inside();
    int countActive(UUID unitId, String typeCode);
    /** Inserción atómica: solo inserta si el inmueble está por debajo del límite. */
    boolean insertIfBelowLimit(Vehicle v, int limit);
    /** Actualización atómica: solo si el vehículo NO está dentro. */
    boolean updateIfOutside(Vehicle v);
    boolean softDeleteIfOutside(UUID id, UUID by, Instant at);
}
