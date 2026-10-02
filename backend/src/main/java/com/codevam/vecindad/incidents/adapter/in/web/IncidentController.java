package com.codevam.vecindad.incidents.adapter.in.web;

import com.codevam.vecindad.incidents.application.IncidentService;
import com.codevam.vecindad.incidents.domain.Incident;
import com.codevam.vecindad.incidents.domain.IncidentCategory;
import com.codevam.vecindad.incidents.domain.IncidentStatus;
import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/incidents")
@Tag(name = "Novedades de portería")
public class IncidentController {

    public record IncidentRequest(@NotNull IncidentCategory category, @NotBlank @Size(max = 2000) String description,
                                  @Size(max = 150) String location, Instant occurredAt) {}
    public record StatusRequest(@NotNull IncidentStatus status, @Size(max = 500) String resolution) {}
    public record AssignRequest(@NotNull UUID userId) {}

    private final IncidentService service;

    public IncidentController(IncidentService service) {
        this.service = service;
    }

    @Operation(summary = "Registra una novedad (el reportante es el usuario autenticado)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('INCIDENTS_CREATE')")
    public Incident create(@Valid @RequestBody IncidentRequest r) {
        return service.create(CurrentUser.requireTenant().userId(),
                new IncidentService.Command(r.category(), r.description(), r.location(), r.occurredAt()));
    }

    @Operation(summary = "Lista novedades (filtros: category, status, from, to)")
    @GetMapping
    @PreAuthorize("hasAuthority('INCIDENTS_VIEW')")
    public PageResult<Incident> list(@RequestParam(required = false) IncidentCategory category,
                                     @RequestParam(required = false) IncidentStatus status,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
                                     @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.search(category, status, from, to, page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('INCIDENTS_VIEW')")
    public Incident get(@PathVariable UUID id) {
        return service.get(id);
    }

    @Operation(summary = "Cambia el estado. Para CLOSED es obligatorio indicar la resolución")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('INCIDENTS_MANAGE')")
    public Incident status(@PathVariable UUID id, @Valid @RequestBody StatusRequest r) {
        return service.updateStatus(id, r.status(), r.resolution());
    }

    @Operation(summary = "Asigna un responsable (debe ser usuario activo de la copropiedad)")
    @PutMapping("/{id}/assignee")
    @PreAuthorize("hasAuthority('INCIDENTS_MANAGE')")
    public Incident assign(@PathVariable UUID id, @Valid @RequestBody AssignRequest r) {
        return service.assign(id, r.userId());
    }
}
