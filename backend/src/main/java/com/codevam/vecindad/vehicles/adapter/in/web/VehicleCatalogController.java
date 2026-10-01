package com.codevam.vecindad.vehicles.adapter.in.web;

import com.codevam.vecindad.vehicles.application.VehicleCatalogService;
import com.codevam.vecindad.vehicles.domain.ParkingSpace;
import com.codevam.vecindad.vehicles.domain.VehicleLimit;
import com.codevam.vecindad.vehicles.domain.VehicleType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Vehículos - configuración")
public class VehicleCatalogController {

    public record TypeRequest(@NotBlank @Size(max = 60) String name, boolean active, boolean requiresPlate) {}
    public record LimitRequest(@NotNull @Min(0) Integer max) {}
    public record ParkingRequest(@NotBlank @Size(max = 30) String code, @NotBlank @Size(max = 15) String kind, UUID unitId) {}

    private final VehicleCatalogService service;

    public VehicleCatalogController(VehicleCatalogService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/vehicle-types")
    @PreAuthorize("hasAnyAuthority('VEHICLES_VIEW','VEHICLES_VIEW_OWN')")
    public List<VehicleType> types() {
        return service.types();
    }

    @Operation(summary = "Crea o modifica un tipo de vehículo (p. ej. BICICLETA_ELECTRICA)")
    @PutMapping("/api/v1/vehicle-types/{code}")
    @PreAuthorize("hasAuthority('VEHICLE_RULES_MANAGE')")
    public VehicleType upsertType(@PathVariable String code, @Valid @RequestBody TypeRequest req) {
        return service.upsertType(code, req.name(), req.active(), req.requiresPlate());
    }

    @GetMapping("/api/v1/vehicle-limits")
    @PreAuthorize("hasAnyAuthority('VEHICLES_VIEW','VEHICLES_VIEW_OWN')")
    public List<VehicleLimit> limits() {
        return service.limits();
    }

    @Operation(summary = "Máximo por defecto de vehículos de un tipo por inmueble")
    @PutMapping("/api/v1/vehicle-limits/{typeCode}")
    @PreAuthorize("hasAuthority('VEHICLE_RULES_MANAGE')")
    public VehicleLimit setDefault(@PathVariable String typeCode, @Valid @RequestBody LimitRequest req) {
        return service.setDefaultLimit(typeCode, req.max());
    }

    @Operation(summary = "Excepción de límite para un inmueble (p. ej. 2 carros por tener parqueadero privado)")
    @PutMapping("/api/v1/properties/{unitId}/vehicle-limits/{typeCode}")
    @PreAuthorize("hasAuthority('VEHICLE_RULES_MANAGE')")
    public VehicleLimit setUnitLimit(@PathVariable UUID unitId, @PathVariable String typeCode, @Valid @RequestBody LimitRequest req) {
        return service.setUnitLimit(unitId, typeCode, req.max());
    }

    @GetMapping("/api/v1/parking-spaces")
    @PreAuthorize("hasAuthority('VEHICLES_VIEW')")
    public List<ParkingSpace> parking() {
        return service.parkingSpaces();
    }

    @PostMapping("/api/v1/parking-spaces")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PARKING_MANAGE')")
    public ParkingSpace createParking(@Valid @RequestBody ParkingRequest req) {
        return service.createParking(req.code(), req.kind(), req.unitId());
    }
}
