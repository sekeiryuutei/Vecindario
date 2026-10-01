package com.codevam.vecindad.shared.tenancy;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Acceso JDBC a tablas de tenant. Cada SQL usa el marcador {s} que se reemplaza por el schema del tenant ACTIVO
 * (ya validado por SchemaNames). Sin tenant en contexto lanza TENANT_NOT_SELECTED: jamás cae en otro schema.
 */
@Component
public class TenantJdbc {
    private final JdbcTemplate jdbc;

    public TenantJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public JdbcTemplate jdbc() {
        return jdbc;
    }

    public String q(String sql) {
        return sql.replace("{s}", SchemaNames.quoted(TenantContext.require().schema()));
    }
}
