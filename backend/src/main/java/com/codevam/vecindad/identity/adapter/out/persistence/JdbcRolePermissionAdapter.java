package com.codevam.vecindad.identity.adapter.out.persistence;

import com.codevam.vecindad.identity.application.port.out.RolePermissionPort;
import com.codevam.vecindad.identity.domain.Roles;
import com.codevam.vecindad.shared.tenancy.SchemaNames;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Repository
public class JdbcRolePermissionAdapter implements RolePermissionPort {
    private final JdbcTemplate jdbc;

    public JdbcRolePermissionAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<String> effective(String tenantSchema, String roleCode) {
        if (Roles.ADMINISTRADOR.equals(roleCode)) {
            return allPermissionCodes();
        }
        String table = SchemaNames.quoted(SchemaNames.requireTenantSchema(tenantSchema)) + ".role_permission_overrides";
        Set<String> result = new HashSet<>(jdbc.queryForList(
                "SELECT permission_code FROM public.role_permissions WHERE role_code = ?", String.class, roleCode));
        jdbc.query("SELECT permission_code, granted FROM " + table + " WHERE role_code = ?", rs -> {
            if (rs.getBoolean("granted")) {
                result.add(rs.getString("permission_code"));
            } else {
                result.remove(rs.getString("permission_code"));
            }
        }, roleCode);
        return result;
    }

    @Override
    public Set<String> allPermissionCodes() {
        return new HashSet<>(jdbc.queryForList("SELECT code FROM public.permissions", String.class));
    }

    @Override
    public boolean permissionExists(String code) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM public.permissions WHERE code = ?", Integer.class, code);
        return n != null && n > 0;
    }

    @Override
    public void setOverride(String tenantSchema, String roleCode, String permissionCode, boolean granted, UUID actor) {
        String table = SchemaNames.quoted(SchemaNames.requireTenantSchema(tenantSchema)) + ".role_permission_overrides";
        jdbc.update("INSERT INTO " + table + " (role_code, permission_code, granted, updated_by) VALUES (?, ?, ?, ?) "
                + "ON CONFLICT (role_code, permission_code) DO UPDATE SET granted = EXCLUDED.granted, "
                + "updated_by = EXCLUDED.updated_by, updated_at = now()", roleCode, permissionCode, granted, actor);
    }

    @Override
    public void clearOverride(String tenantSchema, String roleCode, String permissionCode) {
        String table = SchemaNames.quoted(SchemaNames.requireTenantSchema(tenantSchema)) + ".role_permission_overrides";
        jdbc.update("DELETE FROM " + table + " WHERE role_code = ? AND permission_code = ?", roleCode, permissionCode);
    }
}
