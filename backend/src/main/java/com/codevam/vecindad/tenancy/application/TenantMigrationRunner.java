package com.codevam.vecindad.tenancy.application;

import com.codevam.vecindad.tenancy.application.port.out.TenantPort;
import com.codevam.vecindad.tenancy.application.port.out.TenantSchemaPort;
import com.codevam.vecindad.tenancy.domain.TenantStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/** Al iniciar, aplica las migraciones de tenant pendientes a todas las copropiedades existentes. Fail-fast. */
@Component
@Order(10)
public class TenantMigrationRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(TenantMigrationRunner.class);

    private final TenantPort tenants;
    private final TenantSchemaPort schemas;

    public TenantMigrationRunner(TenantPort tenants, TenantSchemaPort schemas) {
        this.tenants = tenants;
        this.schemas = schemas;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String schema : tenants.schemasWithStatus(List.of(TenantStatus.ACTIVE, TenantStatus.SUSPENDED))) {
            log.info("Migrando schema de tenant {}", schema);
            schemas.migrate(schema);
        }
    }
}
