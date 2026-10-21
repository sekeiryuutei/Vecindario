package com.codevam.vecindad.reservations.adapter.in.web;

import com.codevam.vecindad.reservations.application.CommonAreaService;
import com.codevam.vecindad.reservations.application.ReservationService;
import com.codevam.vecindad.reservations.domain.CommonArea;
import com.codevam.vecindad.reservations.domain.TimeSlot;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/common-areas")
@Tag(name = "Zonas comunes")
public class CommonAreaController {

    public record CommonAreaRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 1000) String description,
            @Min(1) int capacity,
            boolean requiresPayment,
            BigDecimal pricePerSlot,
            BigDecimal depositAmount,
            boolean requiresApproval,
            @NotNull LocalTime openingTime,
            @NotNull LocalTime closingTime,
            @Min(15) int slotDurationMinutes,
            @Min(1) int maxSlotsPerReservation,
            @Min(1) int maxActiveReservationsPerUnit,
            @Min(0) int minAdvanceHours,
            @Min(1) int maxAdvanceDays,
            @Min(0) int cancellationDeadlineHours,
            @Size(max = 4000) String terms,
            LocalTime approvalStartTime,
            LocalTime approvalEndTime
    ) {}

    private final CommonAreaService commonAreaService;
    private final ReservationService reservationService;

    public CommonAreaController(CommonAreaService commonAreaService, ReservationService reservationService) {
        this.commonAreaService = commonAreaService;
        this.reservationService = reservationService;
    }

    @Operation(summary = "Lista zonas comunes")
    @GetMapping
    @PreAuthorize("hasAnyAuthority('RESERVATIONS_VIEW', 'RESERVATIONS_VIEW_OWN', 'COMMON_AREAS_MANAGE')")
    public List<CommonArea> list(@RequestParam(defaultValue = "true") boolean onlyActive) {
        return commonAreaService.list(onlyActive);
    }

    @Operation(summary = "Obtiene detalle de una zona común")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('RESERVATIONS_VIEW', 'RESERVATIONS_VIEW_OWN', 'COMMON_AREAS_MANAGE')")
    public CommonArea getById(@PathVariable UUID id) {
        return commonAreaService.getById(id);
    }

    @Operation(summary = "Crea una zona común")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('COMMON_AREAS_MANAGE')")
    public CommonArea create(@Valid @RequestBody CommonAreaRequest r) {
        return commonAreaService.create(new CommonArea(
                null, r.name(), r.description(), r.capacity(), r.requiresPayment(),
                r.pricePerSlot() == null ? BigDecimal.ZERO : r.pricePerSlot(),
                r.depositAmount() == null ? BigDecimal.ZERO : r.depositAmount(),
                r.requiresApproval(), r.openingTime(), r.closingTime(),
                r.slotDurationMinutes(), r.maxSlotsPerReservation(),
                r.maxActiveReservationsPerUnit(), r.minAdvanceHours(),
                r.maxAdvanceDays(), r.cancellationDeadlineHours(),
                r.terms() == null ? "" : r.terms(), r.approvalStartTime(), r.approvalEndTime(),
                true, null, null
        ));
    }

    @Operation(summary = "Actualiza una zona común")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('COMMON_AREAS_MANAGE')")
    public CommonArea update(@PathVariable UUID id, @Valid @RequestBody CommonAreaRequest r) {
        return commonAreaService.update(id, new CommonArea(
                id, r.name(), r.description(), r.capacity(), r.requiresPayment(),
                r.pricePerSlot() == null ? BigDecimal.ZERO : r.pricePerSlot(),
                r.depositAmount() == null ? BigDecimal.ZERO : r.depositAmount(),
                r.requiresApproval(), r.openingTime(), r.closingTime(),
                r.slotDurationMinutes(), r.maxSlotsPerReservation(),
                r.maxActiveReservationsPerUnit(), r.minAdvanceHours(),
                r.maxAdvanceDays(), r.cancellationDeadlineHours(),
                r.terms() == null ? "" : r.terms(), r.approvalStartTime(), r.approvalEndTime(),
                true, null, null
        ));
    }

    @Operation(summary = "Inactiva una zona común")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('COMMON_AREAS_MANAGE')")
    public void delete(@PathVariable UUID id) {
        commonAreaService.delete(id);
    }

    @Operation(summary = "Consulta disponibilidad de franjas horarias por fecha")
    @GetMapping("/{id}/availability")
    @PreAuthorize("hasAnyAuthority('RESERVATIONS_VIEW', 'RESERVATIONS_VIEW_OWN', 'COMMON_AREAS_MANAGE')")
    public List<TimeSlot> getAvailability(@PathVariable UUID id,
                                          @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return reservationService.getAvailability(id, date);
    }
}

