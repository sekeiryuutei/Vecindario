package com.codevam.vecindad.tenancy.adapter.out;

import com.codevam.vecindad.shared.tenancy.SchemaNames;
import com.codevam.vecindad.tenancy.application.port.out.TenantSchemaPort;
import org.flywaydb.core.Flyway;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;

@Component
public class FlywayTenantSchemaAdapter implements TenantSchemaPort {
    private final DataSource dataSource;

    public FlywayTenantSchemaAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void migrate(String schemaName) {
        SchemaNames.requireTenantSchema(schemaName);
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schemaName)
                .defaultSchema(schemaName)
                .locations("classpath:db/migration/tenant")
                .validateOnMigrate(true)
                .load()
                .migrate();
    }
}
