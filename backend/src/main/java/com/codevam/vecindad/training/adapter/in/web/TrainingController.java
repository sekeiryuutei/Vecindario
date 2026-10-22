package com.codevam.vecindad.training.adapter.in.web;

import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.training.application.TrainingService;
import com.codevam.vecindad.training.domain.EnrollmentStatus;
import com.codevam.vecindad.training.domain.TrainingEnrollment;
import com.codevam.vecindad.training.domain.TrainingModality;
import com.codevam.vecindad.training.domain.TrainingSession;
import com.codevam.vecindad.training.domain.TrainingStatus;
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
@Tag(name = "Capacitaciones comunitarias")
public class TrainingController {
    public record TrainingRequest(@NotBlank @Size(max = 160) String title,
                                  @Size(max = 1000) String description,
                                  @Size(max = 160) String trainerName,
                                  TrainingModality modality, @Size(max = 200) String location,
                                  @Size(max = 500) String meetingLink,
                                  @NotNull Instant startsAt, @NotNull Instant endsAt,
                                  @Min(1) @Max(500) int capacity) {
        TrainingService.Command toCommand() {
            return new TrainingService.Command(title, description, trainerName, modality, location, meetingLink,
                    startsAt, endsAt, capacity);
        }
    }

    public record AttendanceRequest(@NotNull EnrollmentStatus status) {}

    private final TrainingService service;

    public TrainingController(TrainingService service) {
        this.service = service;
    }

    @Operation(summary = "Lista las capacitaciones")
    @GetMapping("/trainings")
    @PreAuthorize("hasAuthority('TRAININGS_VIEW')")
    public PageResult<TrainingSession> search(
            @RequestParam(required = false) TrainingStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.search(status, from, page, size);
    }

    @Operation(summary = "Crea una capacitación")
    @PostMapping("/trainings")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('TRAININGS_MANAGE')")
    public TrainingSession create(@Valid @RequestBody TrainingRequest req) {
        return service.create(req.toCommand());
    }

    @Operation(summary = "Actualiza una capacitación programada")
    @PutMapping("/trainings/{id}")
    @PreAuthorize("hasAuthority('TRAININGS_MANAGE')")
    public TrainingSession update(@PathVariable UUID id, @Valid @RequestBody TrainingRequest req) {
        return service.update(id, req.toCommand());
    }

    @Operation(summary = "Cancela una capacitación")
    @PostMapping("/trainings/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('TRAININGS_MANAGE')")
    public void cancel(@PathVariable UUID id) {
        service.cancel(id);
    }

    @Operation(summary = "Elimina una capacitación sin inscripciones activas")
    @DeleteMapping("/trainings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('TRAININGS_MANAGE')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @Operation(summary = "Lista las inscripciones de una capacitación")
    @GetMapping("/trainings/{id}/enrollments")
    @PreAuthorize("hasAuthority('TRAININGS_MANAGE')")
    public List<TrainingEnrollment> enrollments(@PathVariable UUID id) {
        return service.enrollments(id);
    }

    @Operation(summary = "Registra la asistencia de una persona inscrita")
    @PutMapping("/trainings/enrollments/{id}")
    @PreAuthorize("hasAuthority('TRAININGS_MANAGE')")
    public TrainingEnrollment attendance(@PathVariable UUID id, @Valid @RequestBody AttendanceRequest req) {
        return service.markAttendance(id, req.status());
    }

    @Operation(summary = "Lista las capacitaciones con el estado de inscripción del residente")
    @GetMapping("/my/trainings")
    @PreAuthorize("hasAuthority('TRAININGS_VIEW_OWN')")
    public List<TrainingService.MyTraining> mine() {
        return service.myTrainings();
    }

    @Operation(summary = "Inscribe al residente en una capacitación")
    @PostMapping("/my/trainings/{id}/enroll")
    @PreAuthorize("hasAuthority('TRAININGS_ENROLL_OWN')")
    public TrainingSession enroll(@PathVariable UUID id) {
        return service.enroll(id);
    }

    @Operation(summary = "Cancela la inscripción propia a una capacitación")
    @DeleteMapping("/my/trainings/{id}/enroll")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('TRAININGS_ENROLL_OWN')")
    public void cancelEnrollment(@PathVariable UUID id) {
        service.cancelEnrollment(id);
    }
}
