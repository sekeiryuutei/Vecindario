package com.codevam.vecindad.parcels.adapter.in.web;

import com.codevam.vecindad.parcels.application.ParcelService;
import com.codevam.vecindad.parcels.domain.Parcel;
import com.codevam.vecindad.shared.model.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Paquetería")
public class ParcelController {

    public record ReceiveRequest(@NotNull UUID unitId, @NotBlank @Size(max = 200) String recipientName, @Size(max = 80) String carrier,
                                 @Size(max = 80) String trackingNumber, @Size(max = 300) String description) {}
    public record DeliverRequest(@NotBlank @Size(max = 200) String deliveredTo) {}
    public record ReturnRequest(@Size(max = 300) String note) {}

    private final ParcelService service;

    public ParcelController(ParcelService service) {
        this.service = service;
    }

    @Operation(summary = "Portería registra un paquete recibido para un inmueble")
    @PostMapping("/api/v1/packages")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PACKAGES_MANAGE')")
    public Parcel receive(@Valid @RequestBody ReceiveRequest r) {
        return service.receive(new ParcelService.ReceiveCommand(r.unitId(), r.recipientName(), r.carrier(), r.trackingNumber(), r.description()));
    }

    @Operation(summary = "Lista paquetes (filtros: unitId, status, q por destinatario/guía/transportadora)")
    @GetMapping("/api/v1/packages")
    @PreAuthorize("hasAuthority('PACKAGES_VIEW')")
    public PageResult<Parcel> list(@RequestParam(required = false) UUID unitId, @RequestParam(required = false) String status,
                                   @RequestParam(required = false) String q, @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "20") int size) {
        return service.search(unitId, status, q, page, size);
    }

    @GetMapping("/api/v1/packages/{id}")
    @PreAuthorize("hasAuthority('PACKAGES_VIEW')")
    public Parcel get(@PathVariable UUID id) {
        return service.get(id);
    }

    @Operation(summary = "Registra la entrega indicando a quién se entregó (409 si ya fue entregado o devuelto)")
    @PostMapping("/api/v1/packages/{id}/deliver")
    @PreAuthorize("hasAuthority('PACKAGES_MANAGE')")
    public Parcel deliver(@PathVariable UUID id, @Valid @RequestBody DeliverRequest r) {
        return service.deliver(id, r.deliveredTo());
    }

    @Operation(summary = "Marca el paquete como devuelto a la transportadora")
    @PostMapping("/api/v1/packages/{id}/return")
    @PreAuthorize("hasAuthority('PACKAGES_MANAGE')")
    public Parcel returnParcel(@PathVariable UUID id, @Valid @RequestBody(required = false) ReturnRequest r) {
        return service.returnParcel(id, r == null ? null : r.note());
    }

    @Operation(summary = "Paquetes de mis inmuebles (opcional status)")
    @GetMapping("/api/v1/my/packages")
    @PreAuthorize("hasAuthority('PACKAGES_VIEW_OWN')")
    public List<Parcel> mine(@RequestParam(required = false) String status) {
        return service.mine(status);
    }
}
