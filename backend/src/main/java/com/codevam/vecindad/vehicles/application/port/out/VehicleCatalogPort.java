package com.codevam.vecindad.vehicles.application.port.out;

import com.codevam.vecindad.vehicles.domain.ParkingSpace;
import com.codevam.vecindad.vehicles.domain.VehicleLimit;
import com.codevam.vecindad.vehicles.domain.VehicleType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VehicleCatalogPort {
    List<VehicleType> types();
    Optional<VehicleType> findType(String code);
    void upsertType(VehicleType type);
    List<VehicleLimit> defaultLimits();
    void setDefaultLimit(String typeCode, int max);
    void setUnitLimit(UUID unitId, String typeCode, int max);
    /** Límite efectivo: excepción del inmueble, si no el valor por defecto del tipo; null si no hay ninguno. */
    Integer effectiveLimit(UUID unitId, String typeCode);

    ParkingSpace insertParking(ParkingSpace p);
    List<ParkingSpace> parkingSpaces();
    Optional<ParkingSpace> findParking(UUID id);
}
