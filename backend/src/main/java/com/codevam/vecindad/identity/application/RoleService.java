package com.codevam.vecindad.identity.application;

import com.codevam.vecindad.audit.application.AuditService;
import com.codevam.vecindad.identity.application.port.out.RolePermissionPort;
import com.codevam.vecindad.identity.domain.Roles;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.security.AuthenticatedUser;
import com.codevam.vecindad.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Permisos por rol, ajustables por cada copropiedad (overrides guardados en el schema del tenant). */
@Service
public class RoleService {
    public record RoleView(String role, Set<String> permissions, boolean editable) {}

    private final RolePermissionPort port;
    private final AuditService audit;

    public RoleService(RolePermissionPort port, AuditService audit) {
        this.port = port;
        this.audit = audit;
    }

    public List<RoleView> listRoles() {
        AuthenticatedUser me = CurrentUser.requireTenant();
        return Roles.TENANT_ROLES.stream().sorted().map(role -> new RoleView(role,
                new TreeSet<>(port.effective(me.tenantSchema(), role)), !Roles.ADMINISTRADOR.equals(role))).toList();
    }

    public Set<String> allPermissions() {
        return new TreeSet<>(port.allPermissionCodes());
    }

    @Transactional
    public RoleView setPermission(String role, String permission, boolean granted) {
        AuthenticatedUser me = CurrentUser.requireTenant();
        if (!Roles.TENANT_ROLES.contains(role)) {
            throw ApiException.badRequest("INVALID_ROLE", "El rol indicado no existe.");
        }
        if (Roles.ADMINISTRADOR.equals(role)) {
            throw ApiException.conflict("ROLE_LOCKED", "El rol ADMINISTRADOR siempre conserva todos los permisos.");
        }
        if (!port.permissionExists(permission)) {
            throw ApiException.badRequest("INVALID_PERMISSION", "El permiso indicado no existe.");
        }
        port.setOverride(me.tenantSchema(), role, permission, granted, me.userId());
        audit.log(me.tenantId(), "ROLE_PERMISSION_CHANGED", "ROLE", role, true,
                Map.of("permission", permission, "granted", granted));
        return new RoleView(role, new TreeSet<>(port.effective(me.tenantSchema(), role)), true);
    }
}
