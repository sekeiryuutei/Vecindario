package com.codevam.vecindad.communications.adapter.in.web;

import com.codevam.vecindad.communications.application.CommunicationService;
import com.codevam.vecindad.communications.domain.Communication;
import com.codevam.vecindad.communications.domain.TargetAudience;
import com.codevam.vecindad.shared.model.PageResult;
import com.codevam.vecindad.shared.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/my/communications")
@Tag(name = "Mis Comunicados (Residente)")
public class MyCommunicationController {

    public record ResidentCommunicationItem(
            Communication communication,
            boolean isRead
    ) {}

    private final CommunicationService service;

    public MyCommunicationController(CommunicationService service) {
        this.service = service;
    }

    @Operation(summary = "Lista comunicados y avisos visibles para el usuario")
    @GetMapping
    @PreAuthorize("hasAuthority('COMMUNICATIONS_VIEW')")
    public PageResult<ResidentCommunicationItem> list(
            @RequestParam(required = false) String tower,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        var user = CurrentUser.requireTenant();
        TargetAudience audience = TargetAudience.ALL;
        if (user.roleCode() != null) {
            String roleName = user.roleCode();
            if (roleName.contains("PROPIETARIO")) {
                audience = TargetAudience.OWNERS;
            } else if (roleName.contains("ARRENDATARIO")) {
                audience = TargetAudience.TENANTS;
            } else if (roleName.contains("CONSEJO")) {
                audience = TargetAudience.COUNCIL;
            } else {
                audience = TargetAudience.RESIDENTS;
            }
        }

        PageResult<Communication> raw = service.searchForAudience(audience, tower, page, size);
        List<ResidentCommunicationItem> items = raw.content().stream()
                .map(c -> new ResidentCommunicationItem(c, service.isRead(c.id(), user.userId())))
                .toList();

        return PageResult.of(items, raw.page(), raw.size(), raw.totalElements());
    }

    @Operation(summary = "Marca un comunicado como leído")
    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('COMMUNICATIONS_VIEW')")
    public void markRead(@PathVariable UUID id) {
        var user = CurrentUser.requireTenant();
        service.markAsRead(id, user.userId());
    }
}
