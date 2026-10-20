package com.codevam.vecindad.unit;

import com.codevam.vecindad.audit.application.AuditService;
import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.tenancy.application.TenantAdminService;
import com.codevam.vecindad.tenancy.application.port.out.TenantPort;
import com.codevam.vecindad.tenancy.application.port.out.TenantSchemaPort;
import com.codevam.vecindad.tenancy.domain.Tenant;
import com.codevam.vecindad.tenancy.domain.TenantStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantAdminServiceTest {

    private TenantPort port;
    private AuditService audit;
    private TenantAdminService service;
    private UUID tenantId;
    private Tenant tenant;

    @BeforeEach
    void setUp() {
        port = Mockito.mock(TenantPort.class);
        audit = Mockito.mock(AuditService.class);
        service = new TenantAdminService(port, Mockito.mock(TenantSchemaPort.class), audit);
        tenantId = UUID.randomUUID();
        tenant = new Tenant(tenantId, UUID.randomUUID(), "Copropiedad Norte", "900123", "norte",
                "tenant_norte", "Calle 1", "Bogotá", "Cundinamarca", "3001234567", "admin@norte.local",
                "COP", "America/Bogota", "es-CO", TenantStatus.ACTIVE, Instant.now());
        when(port.findById(tenantId)).thenReturn(Optional.of(tenant));
    }

    @Test
    void updatesEditableTenantDetails() {
        service.updateDetails(tenantId, new TenantAdminService.UpdateTenantCommand(
                "  Copropiedad Norte 2 ", " 900456 ", " Carrera 2 ", " Bogotá ", " Cundinamarca ",
                " 3100000000 ", " ADMIN@NORTE.LOCAL "));

        verify(port).updateDetails(tenantId, "Copropiedad Norte 2", "900456", "Carrera 2", "Bogotá",
                "Cundinamarca", "3100000000", "ADMIN@NORTE.LOCAL");
        verify(audit).log(any(), anyString(), anyString(), anyString(), anyBoolean(), any());
    }

    @Test
    void rejectsInvalidEmailBeforeUpdating() {
        ApiException e = assertThrows(ApiException.class, () -> service.updateDetails(tenantId,
                new TenantAdminService.UpdateTenantCommand("Norte", null, null, null, null, null, "no-es-correo")));
        assertEquals("INVALID_TENANT_EMAIL", e.getCode());
        verify(port, never()).updateDetails(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsBlankName() {
        ApiException e = assertThrows(ApiException.class, () -> service.updateDetails(tenantId,
                new TenantAdminService.UpdateTenantCommand("   ", null, null, null, null, null, null)));
        assertEquals("TENANT_NAME_REQUIRED", e.getCode());
    }

    @Test
    void allowsOnlySelfServiceFeatures() {
        when(port.features(tenantId)).thenReturn(Map.of("WHATSAPP", true));
        Map<String, Boolean> result = service.setSelfServiceFeature(tenantId, "whatsapp", true);
        assertEquals(Map.of("WHATSAPP", true), result);
        verify(port).setFeature(tenantId, "WHATSAPP", true);
    }

    @Test
    void rejectsPlatformOnlyFeature() {
        ApiException e = assertThrows(ApiException.class,
                () -> service.setSelfServiceFeature(tenantId, "ONLINE_PAYMENTS", true));
        assertEquals("TENANT_FEATURE_NOT_SELF_SERVICE", e.getCode());
        verify(port, never()).setFeature(any(), anyString(), anyBoolean());
    }
}
