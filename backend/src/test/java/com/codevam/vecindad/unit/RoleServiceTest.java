package com.codevam.vecindad.unit;

import com.codevam.vecindad.audit.application.AuditService;
import com.codevam.vecindad.identity.application.RoleService;
import com.codevam.vecindad.identity.application.port.out.RolePermissionPort;
import com.codevam.vecindad.identity.domain.Roles;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.security.AuthenticatedUser;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import com.codevam.vecindad.shared.tenancy.TenantRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoleServiceTest {
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final TenantRef TENANT = new TenantRef(TENANT_ID, "tenant_demo_norte");

    private RolePermissionPort port;
    private AuditService audit;
    private RoleService service;

    @BeforeEach
    void setUp() {
        port = Mockito.mock(RolePermissionPort.class);
        audit = Mockito.mock(AuditService.class);
        service = new RoleService(port, audit);
        TenantContext.set(TENANT);
        AuthenticatedUser user = new AuthenticatedUser(USER_ID, "admin@demo.local", "Administrador Demo", null,
                TENANT_ID, "tenant_demo_norte", "ADMINISTRADOR", Set.of("ROLES_VIEW", "ROLES_MANAGE"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, List.of()));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    @Test
    void resetRoleClearsOverridesAndReturnsBasePermissions() {
        when(port.effective("tenant_demo_norte", "RESIDENTE")).thenReturn(Set.of("BOARD_VIEW", "PETS_VIEW_OWN"));

        RoleService.RoleView view = service.resetRole("RESIDENTE");

        assertEquals("RESIDENTE", view.role());
        assertTrue(view.permissions().contains("BOARD_VIEW"));
        verify(port).clearOverrides("tenant_demo_norte", "RESIDENTE");
        verify(audit).log(eq(TENANT_ID), eq("ROLE_PERMISSIONS_RESET"), eq("ROLE"), eq("RESIDENTE"),
                eq(true), anyMap());
    }

    @Test
    void resetRoleRejectsAdministrador() {
        ApiException error = assertThrows(ApiException.class,
                () -> service.resetRole(Roles.ADMINISTRADOR));

        assertEquals("ROLE_LOCKED", error.getCode());
        verify(port, never()).clearOverrides(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void resetRoleRejectsUnknownRole() {
        ApiException error = assertThrows(ApiException.class, () -> service.resetRole("SUPER_ROLE"));

        assertEquals("INVALID_ROLE", error.getCode());
    }

    @Test
    void resetPermissionClearsOnlyThatOverride() {
        when(port.permissionExists("BOARD_VIEW")).thenReturn(true);
        when(port.effective("tenant_demo_norte", "RESIDENTE")).thenReturn(Set.of("BOARD_VIEW"));

        RoleService.RoleView view = service.resetPermission("RESIDENTE", "BOARD_VIEW");

        assertTrue(view.permissions().contains("BOARD_VIEW"));
        verify(port).clearOverride("tenant_demo_norte", "RESIDENTE", "BOARD_VIEW");
        verify(audit).log(eq(TENANT_ID), eq("ROLE_PERMISSION_RESET"), eq("ROLE"), eq("RESIDENTE"),
                eq(true), anyMap());
    }

    @Test
    void setPermissionRejectsAdministrador() {
        ApiException error = assertThrows(ApiException.class,
                () -> service.setPermission(Roles.ADMINISTRADOR, "BOARD_VIEW", false));

        assertEquals("ROLE_LOCKED", error.getCode());
        verify(port, never()).setOverride(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyBoolean(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void setPermissionRejectsUnknownPermission() {
        when(port.permissionExists("NOT_A_PERMISSION")).thenReturn(false);

        ApiException error = assertThrows(ApiException.class,
                () -> service.setPermission("RESIDENTE", "NOT_A_PERMISSION", true));

        assertEquals("INVALID_PERMISSION", error.getCode());
    }

    @Test
    void listRolesMarksAdministradorAsNonEditable() {
        for (String role : Roles.TENANT_ROLES) {
            when(port.effective("tenant_demo_norte", role)).thenReturn(Set.of("ROLE_PERMISSION"));
        }

        RoleService.RoleView resident = service.listRoles().stream()
                .filter(r -> "RESIDENTE".equals(r.role())).findFirst().orElseThrow();
        RoleService.RoleView admin = service.listRoles().stream()
                .filter(r -> Roles.ADMINISTRADOR.equals(r.role())).findFirst().orElseThrow();

        assertTrue(resident.editable());
        assertFalse(admin.editable());
    }
}
