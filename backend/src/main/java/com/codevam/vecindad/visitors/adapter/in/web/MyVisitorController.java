package com.codevam.vecindad.visitors.adapter.in.web;

import com.codevam.vecindad.visitors.application.VisitorService;
import com.codevam.vecindad.visitors.domain.Invitation;
import com.codevam.vecindad.visitors.domain.Visit;
import com.codevam.vecindad.visitors.domain.VisitStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/my/visitors")
@Tag(name = "Visitantes - residente")
public class MyVisitorController {

    public record InvitationRequest(@NotNull UUID unitId, @NotBlank @Size(max = 200) String visitorName,
                                    @Size(max = 30) String documentNumber, @Size(max = 40) String phone, @Size(max = 12) String plate,
                                    @Min(1) @Max(50) Integer peopleCount, @Min(1) @Max(20) Integer maxEntries,
                                    Instant validFrom, @NotNull Instant validTo, @Size(max = 300) String notes) {}
    public record DecisionRequest(@Size(max = 300) String note) {}

    private final VisitorService service;

    public MyVisitorController(VisitorService service) {
        this.service = service;
    }

    @Operation(summary = "Crea una invitación con QR temporal. El qrToken solo se devuelve aquí (se guarda únicamente su hash)")
    @PostMapping("/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('VISITORS_CREATE')")
    public VisitorService.InvitationCreated create(@Valid @RequestBody InvitationRequest r) {
        return service.createInvitation(new VisitorService.InvitationCommand(r.unitId(), r.visitorName(), r.documentNumber(),
                r.phone(), r.plate(), r.peopleCount(), r.maxEntries(), r.validFrom(), r.validTo(), r.notes()));
    }

    @GetMapping("/invitations")
    @PreAuthorize("hasAuthority('VISITORS_VIEW_OWN')")
    public List<Invitation> mine(@RequestParam(required = false) UUID unitId) {
        return service.myInvitations(unitId);
    }

    @Operation(summary = "Cancela una de mis invitaciones")
    @PostMapping("/invitations/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('VISITORS_CREATE')")
    public void cancel(@PathVariable UUID id) {
        service.cancelMine(id);
    }

    @Operation(summary = "Genera un QR nuevo para una invitación activa e invalida el anterior")
    @PostMapping("/invitations/{id}/regenerate-qr")
    @PreAuthorize("hasAuthority('VISITORS_CREATE')")
    public VisitorService.InvitationCreated regenerate(@PathVariable UUID id) {
        return service.regenerateQr(id);
    }

    @Operation(summary = "Solicitudes de ingreso de visitantes sin invitación en mis inmuebles (status=PENDING_AUTH: por responder)")
    @GetMapping("/requests")
    @PreAuthorize("hasAuthority('VISITORS_VIEW_OWN')")
    public List<Visit> requests(@RequestParam(required = false) VisitStatus status) {
        return service.myRequests(status);
    }

    @Operation(summary = "Autoriza el ingreso del visitante")
    @PostMapping("/requests/{id}/authorize")
    @PreAuthorize("hasAuthority('VISITORS_RESPOND_OWN')")
    public Visit authorize(@PathVariable UUID id, @Valid @RequestBody(required = false) DecisionRequest r) {
        return service.decide(id, true, r == null ? null : r.note());
    }

    @Operation(summary = "Rechaza el ingreso del visitante")
    @PostMapping("/requests/{id}/reject")
    @PreAuthorize("hasAuthority('VISITORS_RESPOND_OWN')")
    public Visit reject(@PathVariable UUID id, @Valid @RequestBody(required = false) DecisionRequest r) {
        return service.decide(id, false, r == null ? null : r.note());
    }
}
