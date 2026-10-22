package com.codevam.vecindad.appointments.adapter.in.web;

import com.codevam.vecindad.appointments.application.AppointmentService;
import com.codevam.vecindad.appointments.domain.Appointment;
import com.codevam.vecindad.appointments.domain.AppointmentModality;
import com.codevam.vecindad.appointments.domain.AppointmentStatus;
import com.codevam.vecindad.shared.model.PageResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Citas con la administración")
public class AppointmentController {
    public record AppointmentRequest(@NotNull UUID unitId, @NotBlank @Size(max = 160) String subject,
                                     @NotBlank @Size(max = 1000) String reason, @NotNull Instant scheduledAt,
                                     @Min(15) @Max(120) Integer durationMinutes, AppointmentModality modality,
                                     @Size(max = 200) String location, @Size(max = 500) String meetingLink) {
        AppointmentService.RequestCommand toCommand() {
            return new AppointmentService.RequestCommand(unitId, subject, reason, scheduledAt, durationMinutes,
                    modality, location, meetingLink);
        }
    }

    public record AppointmentUpdateRequest(@Size(max = 160) String subject, @Size(max = 1000) String reason,
                                           Instant scheduledAt, @Min(15) @Max(120) Integer durationMinutes,
                                           AppointmentModality modality, @Size(max = 200) String location,
                                           @Size(max = 500) String meetingLink, AppointmentStatus status,
                                           @Size(max = 1000) String adminNotes) {
        AppointmentService.UpdateCommand toCommand() {
            return new AppointmentService.UpdateCommand(subject, reason, scheduledAt, durationMinutes, modality,
                    location, meetingLink, status, adminNotes);
        }
    }

    public record CancelRequest(@Size(max = 500) String reason) {}

    private final AppointmentService service;

    public AppointmentController(AppointmentService service) {
        this.service = service;
    }

    @Operation(summary = "Lista todas las citas con filtros de estado y fecha")
    @GetMapping("/appointments")
    @PreAuthorize("hasAuthority('APPOINTMENTS_VIEW')")
    public PageResult<Appointment> search(
            @RequestParam(required = false) AppointmentStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.search(status, from, to, q, page, size);
    }

    @Operation(summary = "Actualiza o cambia el estado de una cita")
    @PutMapping("/appointments/{id}")
    @PreAuthorize("hasAuthority('APPOINTMENTS_MANAGE')")
    public Appointment update(@PathVariable UUID id, @Valid @RequestBody AppointmentUpdateRequest req) {
        return service.update(id, req.toCommand());
    }

    @Operation(summary = "Elimina lógicamente una cita")
    @DeleteMapping("/appointments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('APPOINTMENTS_MANAGE')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @Operation(summary = "Lista las citas del residente autenticado")
    @GetMapping("/my/appointments")
    @PreAuthorize("hasAuthority('APPOINTMENTS_VIEW_OWN')")
    public List<Appointment> mine() {
        return service.myAppointments();
    }

    @Operation(summary = "Solicita una cita con la administración")
    @PostMapping("/my/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('APPOINTMENTS_REQUEST')")
    public Appointment request(@Valid @RequestBody AppointmentRequest req) {
        return service.request(req.toCommand());
    }

    @Operation(summary = "Cancela una cita propia")
    @PostMapping("/my/appointments/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('APPOINTMENTS_REQUEST')")
    public void cancel(@PathVariable UUID id, @RequestBody(required = false) CancelRequest req) {
        service.cancelOwn(id, req == null ? null : req.reason());
    }
}
