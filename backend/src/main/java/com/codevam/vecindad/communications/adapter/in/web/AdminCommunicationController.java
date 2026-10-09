package com.codevam.vecindad.communications.adapter.in.web;

import com.codevam.vecindad.communications.application.CommunicationService;
import com.codevam.vecindad.communications.domain.Communication;
import com.codevam.vecindad.communications.domain.CommunicationPriority;
import com.codevam.vecindad.communications.domain.CommunicationStatus;
import com.codevam.vecindad.communications.domain.TargetAudience;
import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/communications")
@Tag(name = "Comunicados (Administración)")
public class AdminCommunicationController {

    public record CreateCommunicationRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 500) String summary,
            @NotBlank @Size(max = 10000) String content,
            CommunicationPriority priority,
            TargetAudience targetAudience,
            @Size(max = 50) String targetTower,
            boolean isPinned,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant expiresAt,
            boolean publishNow
    ) {}

    private final CommunicationService service;

    public AdminCommunicationController(CommunicationService service) {
        this.service = service;
    }

    @Operation(summary = "Lista todos los comunicados por estado")
    @GetMapping
    @PreAuthorize("hasAuthority('COMMUNICATIONS_MANAGE')")
    public PageResult<Communication> list(
            @RequestParam(required = false) CommunicationStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return service.search(status, page, size);
    }

    @Operation(summary = "Obtiene detalle de un comunicado")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('COMMUNICATIONS_MANAGE')")
    public Communication getById(@PathVariable UUID id) {
        return service.getById(id);
    }

    @Operation(summary = "Crea un comunicado o borrador")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('COMMUNICATIONS_MANAGE')")
    public Communication create(@Valid @RequestBody CreateCommunicationRequest r) {
        UUID userId = CurrentUser.requireTenant().userId();
        return service.create(userId, new CommunicationService.CreateCommunicationCommand(
                r.title(), r.summary(), r.content(), r.priority(), r.targetAudience(),
                r.targetTower(), r.isPinned(), r.expiresAt(), r.publishNow()
        ));
    }

    @Operation(summary = "Publica un comunicado borrador")
    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('COMMUNICATIONS_MANAGE')")
    public Communication publish(@PathVariable UUID id) {
        return service.publish(id);
    }

    @Operation(summary = "Archiva un comunicado publicado")
    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAuthority('COMMUNICATIONS_MANAGE')")
    public Communication archive(@PathVariable UUID id) {
        return service.archive(id);
    }

    @Operation(summary = "Elimina un comunicado")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('COMMUNICATIONS_MANAGE')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}

