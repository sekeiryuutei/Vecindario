package com.codevam.vecindad.tenancy.application.port.out;

public interface TenantSchemaPort {
    /** Crea el schema si no existe y aplica las migraciones pendientes de tenant (idempotente). */
    void migrate(String schemaName);
}
