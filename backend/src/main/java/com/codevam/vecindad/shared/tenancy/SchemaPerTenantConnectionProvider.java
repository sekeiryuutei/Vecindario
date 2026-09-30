package com.codevam.vecindad.shared.tenancy;

import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Entrega conexiones con search_path fijado al schema del tenant (más public como respaldo)
 * y lo restablece a public al devolver la conexión al pool.
 */
@Component
public class SchemaPerTenantConnectionProvider implements MultiTenantConnectionProvider<String> {

    private final DataSource dataSource;

    public SchemaPerTenantConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        String schema = SchemaNames.requireKnown(tenantIdentifier);
        Connection connection = dataSource.getConnection();
        try (Statement st = connection.createStatement()) {
            if (SchemaNames.PUBLIC.equals(schema)) {
                st.execute("SET search_path TO public");
            } else {
                st.execute("SET search_path TO " + SchemaNames.quoted(schema) + ", public");
            }
        } catch (SQLException e) {
            connection.close();
            throw e;
        }
        return connection;
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("SET search_path TO public");
        } finally {
            connection.close();
        }
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return false;
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        throw new UnsupportedOperationException("No se puede desempaquetar como " + unwrapType);
    }
}
