package com.codevam.vecindad.unit;

import com.codevam.vecindad.shared.tenancy.SchemaNames;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class SchemaNamesTest {

    @Test
    void acceptsValidTenantSchema() {
        assertEquals("tenant_demo_norte", SchemaNames.requireTenantSchema("tenant_demo_norte"));
        assertEquals("tenant_demo_norte", SchemaNames.fromSlug("demo_norte"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"public", "tenant_", "tenant_A", "tenant_x; DROP SCHEMA public", "tenant_\"x", "pg_catalog",
            "tenant_ab", "tenant_1abc", "TENANT_abc", " tenant_abc"})
    void rejectsInvalidTenantSchemas(String name) {
        assertThrows(IllegalArgumentException.class, () -> SchemaNames.requireTenantSchema(name));
    }

    @Test
    void publicIsKnownButNotATenantSchema() {
        assertEquals("public", SchemaNames.requireKnown("public"));
        assertFalse(SchemaNames.isTenantSchema("public"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "1abc", "Abc", "a-b-c", "a b c", "", "x'--"})
    void rejectsInvalidSlugs(String slug) {
        assertFalse(SchemaNames.isValidSlug(slug));
        assertThrows(IllegalArgumentException.class, () -> SchemaNames.fromSlug(slug));
    }
}
