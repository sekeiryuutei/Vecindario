package com.codevam.vecindad.unit;

import com.codevam.vecindad.shared.error.ApiException;
import com.codevam.vecindad.shared.tenancy.TenantContext;
import com.codevam.vecindad.shared.tenancy.TenantRef;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TenantContextTest {

    @AfterEach
    void cleanup() {
        TenantContext.clear();
    }

    @Test
    void requireFailsWithoutTenant() {
        ApiException e = assertThrows(ApiException.class, TenantContext::require);
        assertEquals("TENANT_NOT_SELECTED", e.getCode());
    }

    @Test
    void callAsRestoresPreviousContext() {
        TenantRef a = new TenantRef(UUID.randomUUID(), "tenant_aaa");
        TenantRef b = new TenantRef(UUID.randomUUID(), "tenant_bbb");
        TenantContext.set(a);
        String inner = TenantContext.callAs(b, () -> TenantContext.currentSchema().orElseThrow());
        assertEquals("tenant_bbb", inner);
        assertEquals("tenant_aaa", TenantContext.currentSchema().orElseThrow());
    }

    @Test
    void callAsClearsWhenThereWasNoPreviousContext() {
        TenantContext.runAs(new TenantRef(UUID.randomUUID(), "tenant_aaa"), () -> assertTrue(TenantContext.current().isPresent()));
        assertTrue(TenantContext.current().isEmpty());
    }

    @Test
    void rejectsInvalidSchema() {
        assertThrows(IllegalArgumentException.class, () -> TenantContext.set(new TenantRef(UUID.randomUUID(), "public")));
    }
}
