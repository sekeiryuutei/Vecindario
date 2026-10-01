package com.codevam.vecindad.vehicles.adapter.in.web;

import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.vehicles.application.VehicleService;
import com.codevam.vecindad.vehicles.domain.Presence;
import com.codevam.vecindad.vehicles.domain.Vehicle;
import com.codevam.vecindad.vehicles.domain.VehicleStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Vehículos")
public class VehicleController {

    public record VehicleRequest(@NotBlank @Size(max = 30) String typeCode, @Size(max = 12) String plate,
                                 @Size(max = 60) String brand, @Size(max = 60) String model, @Size(max = 40) String color,
                                 @Min(1900) @Max(2100) Integer modelYear, UUID ownerPersonId, UUID unitId,
                                 UUID parkingSpaceId, VehicleStatus status) {
        VehicleService.Command toCommand() {
            return new VehicleService.Command(typeCode, plate, brand, model, color, modelYear, ownerPersonId, unitId,
                    parkingSpaceId, status);
        }
    }

    private final VehicleService service;

    public VehicleController(VehicleService service) {
        this.service = service;
    }

    @Operation(summary = "Lista vehículos (filtros: plate, unitId, presence=INSIDE|OUTSIDE)")
    @GetMapping("/api/v1/vehicles")
    @PreAuthorize("hasAuthority('VEHICLES_VIEW')")
    public PageResult<Vehicle> list(@RequestParam(required = false) String plate, @RequestParam(required = false) UUID unitId,
                                    @RequestParam(required = false) Presence presence,
                                    @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.search(plate, unitId, presence, page, size);
    }

    @GetMapping("/api/v1/vehicles/{id}")
    @PreAuthorize("hasAuthority('VEHICLES_VIEW')")
    public Vehicle get(@PathVariable UUID id) {
        return service.get(id);
    }

    @Operation(summary = "Registra un vehículo; valida tipo, placa única y límite por tipo/inmueble")
    @PostMapping("/api/v1/vehicles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('VEHICLES_CREATE')")
    public Vehicle create(@Valid @RequestBody VehicleRequest req) {
        return service.create(req.toCommand());
    }

    @Operation(summary = "Modifica un vehículo. 409 VEHICLE_INSIDE si está dentro de la copropiedad")
    @PutMapping("/api/v1/vehicles/{id}")
    @PreAuthorize("hasAuthority('VEHICLES_UPDATE')")
    public Vehicle update(@PathVariable UUID id, @Valid @RequestBody VehicleRequest req) {
        return service.update(id, req.toCommand());
    }

    @Operation(summary = "Elimina lógicamente un vehículo. 409 VEHICLE_INSIDE si está dentro")
    @DeleteMapping("/api/v1/vehicles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('VEHICLES_DELETE')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    // ---------------------------------------------- residente: solo vehículos de sus inmuebles

    @Operation(summary = "Vehículos de mis inmuebles (opcionalmente de uno: unitId)")
    @GetMapping("/api/v1/my/vehicles")
    @PreAuthorize("hasAuthority('VEHICLES_VIEW_OWN')")
    public List<Vehicle> myVehicles(@RequestParam(required = false) UUID unitId) {
        return service.listOwn(unitId);
    }

    @Operation(summary = "Registra un vehículo en uno de mis inmuebles (el parqueadero y el estado los define la administración)")
    @PostMapping("/api/v1/my/vehicles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('VEHICLES_MANAGE_OWN')")
    public Vehicle createMine(@Valid @RequestBody VehicleRequest req) {
        return service.createOwn(req.toCommand());
    }

    @Operation(summary = "Modifica un vehículo mío. 409 VEHICLE_INSIDE si está dentro")
    @PutMapping("/api/v1/my/vehicles/{id}")
    @PreAuthorize("hasAuthority('VEHICLES_MANAGE_OWN')")
    public Vehicle updateMine(@PathVariable UUID id, @Valid @RequestBody VehicleRequest req) {
        return service.updateOwn(id, req.toCommand());
    }

    @Operation(summary = "Elimina un vehículo mío. 409 VEHICLE_INSIDE si está dentro")
    @DeleteMapping("/api/v1/my/vehicles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('VEHICLES_MANAGE_OWN')")
    public void deleteMine(@PathVariable UUID id) {
        service.deleteOwn(id);
    }
}
