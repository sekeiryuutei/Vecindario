package com.codevam.vecindad.pqrs.adapter.in.web;

import com.codevam.vecindad.pqrs.application.PqrService;
import com.codevam.vecindad.pqrs.domain.*;
import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pqrs")
@Tag(name = "PQRS y Convivencia (Administración)")
public class AdminPqrController {

    public record StatusRequest(
            @NotNull PqrStatus status,
            @Size(max = 2000) String resolutionNotes
    ) {}

    public record AssignRequest(
            @NotNull UUID userId
    ) {}

    public record MessageRequest(
            @NotBlank @Size(max = 2000) String message,
            boolean isInternal
    ) {}

    private final PqrService service;

    public AdminPqrController(PqrService service) {
        this.service = service;
    }

    @Operation(summary = "Lista PQRS con filtros operativos y SLA")
    @GetMapping
    @PreAuthorize("hasAuthority('PQR_VIEW')")
    public PageResult<Pqr> list(
            @RequestParam(required = false) PqrCategory category,
            @RequestParam(required = false) PqrStatus status,
            @RequestParam(required = false) PqrPriority priority,
            @RequestParam(required = false) Boolean overdue,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.search(category, status, priority, overdue, page, size);
    }

    @Operation(summary = "Obtiene detalle de una PQR e historial de mensajes")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PQR_VIEW')")
    public PqrService.PqrDetail getDetail(@PathVariable UUID id) {
        return service.getDetail(id, true);
    }

    @Operation(summary = "Actualiza el estado y resolución de una PQR")
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('PQR_RESPOND', 'PQR_CLOSE')")
    public Pqr updateStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest r) {
        return service.updateStatus(id, r.status(), r.resolutionNotes());
    }

    @Operation(summary = "Asigna una PQR a un usuario responsable")
    @PutMapping("/{id}/assignee")
    @PreAuthorize("hasAuthority('PQR_ASSIGN')")
    public Pqr assign(@PathVariable UUID id, @Valid @RequestBody AssignRequest r) {
        return service.assign(id, r.userId());
    }

    @Operation(summary = "Agrega una respuesta o nota interna en el hilo de la PQR")
    @PostMapping("/{id}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PQR_RESPOND')")
    public PqrMessage addMessage(@PathVariable UUID id, @Valid @RequestBody MessageRequest r) {
        var user = CurrentUser.requireTenant();
        String senderName = user.roleCode() != null ? user.roleCode() : "Administración";
        return service.addMessage(id, user.userId(), senderName, r.message(), r.isInternal());
    }

    @Operation(summary = "Métricas de cumplimiento y SLA de PQRS")
    @GetMapping("/metrics/sla")
    @PreAuthorize("hasAuthority('PQR_VIEW')")
    public PqrSlaMetrics getSlaMetrics() {
        return service.getSlaMetrics();
    }
}
