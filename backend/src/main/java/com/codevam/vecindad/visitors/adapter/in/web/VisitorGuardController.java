package com.codevam.vecindad.visitors.adapter.in.web;

import com.codevam.vecindad.shared.model.PageResult;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/visitors")
@Tag(name = "Portería - visitantes")
public class VisitorGuardController {

    public record QrRequest(@NotBlank @Size(max = 200) String token) {}
    public record WalkInRequest(@NotNull UUID unitId, @NotBlank @Size(max = 200) String visitorName,
                                @Size(max = 30) String documentNumber, @Size(max = 40) String phone, @Size(max = 12) String plate,
                                @Min(1) @Max(50) Integer peopleCount, @Size(max = 300) String note) {}

    private final VisitorService service;

    public VisitorGuardController(VisitorService service) {
        this.service = service;
    }

    @Operation(summary = "Valida un QR sin registrar el ingreso (indica si es utilizable y por qué no)")
    @PostMapping("/validate-qr")
    @PreAuthorize("hasAuthority('VISITORS_AUTHORIZE')")
    public VisitorService.QrValidation validate(@Valid @RequestBody QrRequest req) {
        return service.validateQr(req.token());
    }

    @Operation(summary = "Registra el ingreso con QR. Atómico: un QR de un solo uso entra una sola vez aunque se lea varias veces a la vez")
    @PostMapping("/check-in-qr")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('VISITORS_AUTHORIZE')")
    public Visit checkInQr(@Valid @RequestBody QrRequest req) {
        return service.checkInByQr(req.token());
    }

    @Operation(summary = "Visitante sin invitación: crea la solicitud que el residente debe autorizar o rechazar")
    @PostMapping("/walk-in")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('VISITORS_AUTHORIZE')")
    public Visit walkIn(@Valid @RequestBody WalkInRequest r) {
        return service.walkIn(new VisitorService.WalkInCommand(r.unitId(), r.visitorName(), r.documentNumber(), r.phone(),
                r.plate(), r.peopleCount(), r.note()));
    }

    @Operation(summary = "Registra el ingreso de un visitante ya AUTORIZADO por el residente")
    @PostMapping("/visits/{id}/check-in")
    @PreAuthorize("hasAuthority('VISITORS_AUTHORIZE')")
    public Visit checkIn(@PathVariable UUID id) {
        return service.checkInVisit(id);
    }

    @Operation(summary = "Registra la salida del visitante")
    @PostMapping("/visits/{id}/check-out")
    @PreAuthorize("hasAuthority('VISITORS_AUTHORIZE')")
    public Visit checkOut(@PathVariable UUID id) {
        return service.checkOut(id);
    }

    @Operation(summary = "Visitas (filtros: unitId, status). Con status=INSIDE son los visitantes activos")
    @GetMapping("/visits")
    @PreAuthorize("hasAuthority('VISITORS_VIEW')")
    public PageResult<Visit> visits(@RequestParam(required = false) UUID unitId, @RequestParam(required = false) VisitStatus status,
                                    @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.visits(unitId, status, page, size);
    }

    @Operation(summary = "Invitaciones (validNow=true: solo las vigentes ahora; unitId opcional)")
    @GetMapping("/invitations")
    @PreAuthorize("hasAuthority('VISITORS_VIEW')")
    public PageResult<Invitation> invitations(@RequestParam(required = false) UUID unitId,
                                              @RequestParam(defaultValue = "false") boolean validNow,
                                              @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.invitations(unitId, validNow, page, size);
    }
}
