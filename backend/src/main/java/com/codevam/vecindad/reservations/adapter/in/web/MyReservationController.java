package com.codevam.vecindad.reservations.adapter.in.web;

import com.codevam.vecindad.people.application.port.out.PersonPort;
import com.codevam.vecindad.people.application.port.out.UnitRelationPort;
import com.codevam.vecindad.people.domain.Person;
import com.codevam.vecindad.reservations.application.ReservationService;
import com.codevam.vecindad.reservations.domain.PaymentMethod;
import com.codevam.vecindad.reservations.domain.Reservation;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/my/reservations")
@Tag(name = "Mis Reservas (Residente)")
public class MyReservationController {

    public record CreateMyReservationRequest(
            @NotNull UUID commonAreaId,
            @NotNull UUID unitId,
            @NotNull Instant startTime,
            @NotNull Instant endTime,
            PaymentMethod paymentMethod,
            @Size(max = 100) String paymentReference,
            @Size(max = 500) String notes
    ) {}

    public record CancelMyReservationRequest(@Size(max = 500) String reason) {}

    private final ReservationService service;
    private final PersonPort personPort;
    private final UnitRelationPort relationPort;

    public MyReservationController(ReservationService service, PersonPort personPort, UnitRelationPort relationPort) {
        this.service = service;
        this.personPort = personPort;
        this.relationPort = relationPort;
    }

    @Operation(summary = "Lista reservas de un inmueble propio")
    @GetMapping
    @PreAuthorize("hasAuthority('RESERVATIONS_VIEW_OWN')")
    public List<Reservation> list(@RequestParam UUID unitId) {
        ensureUnitAccess(unitId);
        return service.findByUnit(unitId);
    }

    @Operation(summary = "Crea una reserva para un inmueble propio")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RESERVATIONS_MANAGE_OWN')")
    public Reservation create(@Valid @RequestBody CreateMyReservationRequest r) {
        UUID personId = ensureUnitAccess(r.unitId());
        UUID userId = CurrentUser.requireTenant().userId();
        return service.createReservation(userId, new ReservationService.CreateReservationCommand(
                r.commonAreaId(), r.unitId(), personId, r.startTime(), r.endTime(),
                r.paymentMethod(), r.paymentReference(), r.notes()
        ));
    }

    @Operation(summary = "Cancela una reserva de un inmueble propio")
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('RESERVATIONS_MANAGE_OWN')")
    public Reservation cancel(@PathVariable UUID id, @RequestBody(required = false) CancelMyReservationRequest req) {
        Reservation reservation = service.getById(id);
        ensureUnitAccess(reservation.unitId());
        String reason = req != null ? req.reason() : null;
        return service.cancelReservation(id, reason, false);
    }

    private UUID ensureUnitAccess(UUID unitId) {
        UUID userId = CurrentUser.requireTenant().userId();
        if (!relationPort.userHasUnit(userId, unitId)) {
            throw ApiException.notFound("Inmueble no encontrado.");
        }
        return personPort.findByUserId(userId)
                .map(Person::id)
                .orElse(null);
    }
}

