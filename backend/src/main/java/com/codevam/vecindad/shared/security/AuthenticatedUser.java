package com.codevam.vecindad.shared.security;

import java.util.Set;
import java.util.UUID;

/** Principal autenticado. Rol y permisos se resuelven desde BD en cada petición (no desde el JWT). */
public record AuthenticatedUser(UUID userId, String email, String fullName, String platformRole,
                                UUID tenantId, String tenantSchema, String roleCode, Set<String> permissions) {

    public boolean hasTenant() { return tenantId != null; }

    public boolean isPlatformAdmin() { return "SUPER_ADMIN_PLATFORM".equals(platformRole); }
}
