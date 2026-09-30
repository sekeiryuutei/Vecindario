package com.codevam.vecindad.identity.adapter.in.web;

import com.codevam.vecindad.identity.application.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/roles")
@Tag(name = "Roles y permisos")
public class RoleController {

    public record PermissionChangeRequest(@NotNull Boolean granted) {}

    private final RoleService service;

    public RoleController(RoleService service) {
        this.service = service;
    }

    @Operation(summary = "Roles de la copropiedad con sus permisos efectivos")
    @GetMapping
    @PreAuthorize("hasAuthority('ROLES_MANAGE')")
    public List<RoleService.RoleView> list() {
        return service.listRoles();
    }

    @Operation(summary = "Catálogo de permisos disponibles")
    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('ROLES_MANAGE')")
    public Set<String> permissions() {
        return service.allPermissions();
    }

    @Operation(summary = "Concede o retira un permiso a un rol solo para esta copropiedad")
    @PutMapping("/{role}/permissions/{permission}")
    @PreAuthorize("hasAuthority('ROLES_MANAGE')")
    public RoleService.RoleView setPermission(@PathVariable String role, @PathVariable String permission,
                                              @Valid @RequestBody PermissionChangeRequest req) {
        return service.setPermission(role, permission, req.granted());
    }
}
