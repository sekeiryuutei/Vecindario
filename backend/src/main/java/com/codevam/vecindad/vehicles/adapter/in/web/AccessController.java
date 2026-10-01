package com.codevam.vecindad.vehicles.adapter.in.web;

import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.vehicles.application.AccessService;
import com.codevam.vecindad.vehicles.domain.AccessEvent;
import com.codevam.vecindad.vehicles.domain.SecurityAlert;
import com.codevam.vecindad.vehicles.domain.Vehicle;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/access")
@Tag(name = "Portería - control de acceso vehicular")
public class AccessController {

    public record AccessRequest(@NotBlank @Size(max = 12) String plate, @Size(max = 20) String method,
                                @Size(max = 60) String cameraId) {}

    private final AccessService service;

    public AccessController(AccessService service) {
        this.service = service;
    }

    @Operation(summary = "Busca un vehículo por placa (vista mínima para portería)")
    @GetMapping("/vehicles/lookup")
    @PreAuthorize("hasAuthority('VEHICLES_VIEW')")
    public AccessService.VehicleLookup lookup(@RequestParam String plate) {
        return service.lookup(plate);
    }

    @Operation(summary = "Vehículos que están actualmente dentro de la copropiedad")
    @GetMapping("/vehicles/inside")
    @PreAuthorize("hasAuthority('VEHICLES_VIEW')")
    public List<Vehicle> inside() {
        return service.inside();
    }

    @Operation(summary = "Registra la ENTRADA por placa. Atómico: protege contra solicitudes simultáneas")
    @PostMapping("/entry")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('VEHICLES_REGISTER_ENTRY')")
    public AccessEvent entry(@Valid @RequestBody AccessRequest req) {
        return service.registerEntry(req.plate(), req.method(), req.cameraId());
    }

    @Operation(summary = "Registra la SALIDA por placa")
    @PostMapping("/exit")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('VEHICLES_REGISTER_EXIT')")
    public AccessEvent exit(@Valid @RequestBody AccessRequest req) {
        return service.registerExit(req.plate(), req.method(), req.cameraId());
    }

    @Operation(summary = "Historial de entradas y salidas (filtros: plate, unitId, from, to)")
    @GetMapping("/events")
    @PreAuthorize("hasAuthority('VEHICLES_VIEW')")
    public PageResult<AccessEvent> events(@RequestParam(required = false) String plate, @RequestParam(required = false) UUID unitId,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                          @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.events(plate, unitId, from, to, page, size);
    }

    @Operation(summary = "Alertas de seguridad (por defecto solo abiertas)")
    @GetMapping("/alerts")
    @PreAuthorize("hasAuthority('SECURITY_ALERTS_VIEW')")
    public PageResult<SecurityAlert> alerts(@RequestParam(defaultValue = "true") boolean onlyOpen,
                                            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.alerts(onlyOpen, page, size);
    }

    @PostMapping("/alerts/{id}/resolve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('SECURITY_ALERTS_VIEW')")
    public void resolve(@PathVariable UUID id) {
        service.resolveAlert(id);
    }
}
