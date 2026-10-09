package com.codevam.vecindad.reservations.adapter.in.web;

import com.codevam.vecindad.reservations.application.ReservationService;
import com.codevam.vecindad.reservations.domain.Reservation;
import com.codevam.vecindad.reservations.domain.ReservationStatus;
import com.codevam.vecindad.shared.model.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reservations")
@Tag(name = "Reservas (Administración)")
public class ReservationController {

    public record RejectRequest(@Size(max = 500) String reason) {}
    public record CancelRequest(@Size(max = 500) String reason) {}

    private final ReservationService service;

    public ReservationController(ReservationService service) {
        this.service = service;
    }

    @Operation(summary = "Busca reservas con filtros")
    @GetMapping
    @PreAuthorize("hasAuthority('RESERVATIONS_VIEW')")
    public PageResult<Reservation> list(
            @RequestParam(required = false) UUID commonAreaId,
            @RequestParam(required = false) UUID unitId,
            @RequestParam(required = false) ReservationStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.search(commonAreaId, unitId, status, from, to, page, size);
    }

    @Operation(summary = "Obtiene una reserva por ID")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('RESERVATIONS_VIEW')")
    public Reservation getById(@PathVariable UUID id) {
        return service.getById(id);
    }

    @Operation(summary = "Aprueba una reserva pendiente")
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('RESERVATIONS_APPROVE')")
    public Reservation approve(@PathVariable UUID id) {
        return service.approveReservation(id);
    }

    @Operation(summary = "Rechaza una reserva pendiente")
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('RESERVATIONS_APPROVE')")
    public Reservation reject(@PathVariable UUID id, @RequestBody(required = false) RejectRequest req) {
        String reason = req != null ? req.reason() : null;
        return service.rejectReservation(id, reason);
    }

    @Operation(summary = "Cancela una reserva (administración)")
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('RESERVATIONS_APPROVE')")
    public Reservation cancel(@PathVariable UUID id, @RequestBody(required = false) CancelRequest req) {
        String reason = req != null ? req.reason() : null;
        return service.cancelReservation(id, reason, true);
    }
}

