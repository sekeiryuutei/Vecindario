package com.codevam.vecindad.identity.application.port.out;

import java.util.Set;
import java.util.UUID;

public interface RolePermissionPort {
    /** Permisos efectivos: base del rol + ajustes propios de la copropiedad. ADMINISTRADOR siempre tiene todos. */
    Set<String> effective(String tenantSchema, String roleCode);
    Set<String> allPermissionCodes();
    boolean permissionExists(String code);
    void setOverride(String tenantSchema, String roleCode, String permissionCode, boolean granted, UUID actor);
    void clearOverride(String tenantSchema, String roleCode, String permissionCode);
}
