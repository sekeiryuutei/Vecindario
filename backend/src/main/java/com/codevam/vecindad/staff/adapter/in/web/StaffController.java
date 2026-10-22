package com.codevam.vecindad.staff.adapter.in.web;

import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.staff.application.StaffService;
import com.codevam.vecindad.staff.domain.StaffMember;
import com.codevam.vecindad.staff.domain.StaffStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/staff")
@Tag(name = "Personal operativo")
public class StaffController {
    public record StaffRequest(@NotBlank @Size(max = 200) String fullName,
                               @Size(max = 20) String documentType,
                               @NotBlank @Size(max = 30) String documentNumber,
                               @NotBlank @Size(max = 100) String position,
                               @Size(max = 100) String area, @Size(max = 40) String phone,
                               @Email @Size(max = 254) String email, @Size(max = 80) String shift,
                               LocalDate startDate, LocalDate endDate, StaffStatus status,
                               @Size(max = 500) String notes) {
        StaffService.Command toCommand() {
            return new StaffService.Command(fullName, documentType, documentNumber, position, area, phone, email,
                    shift, startDate, endDate, status, notes);
        }
    }

    private final StaffService service;

    public StaffController(StaffService service) {
        this.service = service;
    }

    @Operation(summary = "Lista el personal operativo")
    @GetMapping
    @PreAuthorize("hasAuthority('STAFF_VIEW')")
    public PageResult<StaffMember> search(@RequestParam(required = false) String q,
                                          @RequestParam(required = false) StaffStatus status,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return service.search(q, status, page, size);
    }

    @Operation(summary = "Registra un miembro del personal operativo")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('STAFF_MANAGE')")
    public StaffMember create(@Valid @RequestBody StaffRequest req) {
        return service.create(req.toCommand());
    }

    @Operation(summary = "Actualiza un miembro del personal operativo")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('STAFF_MANAGE')")
    public StaffMember update(@PathVariable UUID id, @Valid @RequestBody StaffRequest req) {
        return service.update(id, req.toCommand());
    }

    @Operation(summary = "Elimina lógicamente un miembro del personal operativo")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('STAFF_MANAGE')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
