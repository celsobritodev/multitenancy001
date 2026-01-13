package brito.com.multitenancy001.infrastructure.multitenancy.hibernate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.jdbc.connections.spi.AbstractDataSourceBasedMultiTenantConnectionProviderImpl;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class TenantSchemaConnectionProvider
        extends AbstractDataSourceBasedMultiTenantConnectionProviderImpl<String> {

    private static final long serialVersionUID = 1L;
    private static final String DEFAULT_SCHEMA = "public";

    private final DataSource dataSource;

    @Override
    protected DataSource selectAnyDataSource() {
        return dataSource;
    }

    @Override
    protected DataSource selectDataSource(String tenantIdentifier) {
        return dataSource;
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {

        long threadId = Thread.currentThread().threadId();
        String threadTenant = CurrentTenantSchemaResolver.resolveBoundTenantOrDefault();

        String effectiveTenant = StringUtils.hasText(tenantIdentifier)
                ? tenantIdentifier
                : DEFAULT_SCHEMA;

        if (!StringUtils.hasText(tenantIdentifier)) {
            log.warn("⚠️ [MT] tenantIdentifier vazio → usando DEFAULT ({}) | threadTenant={}",
                    DEFAULT_SCHEMA, threadTenant);
        }

        validateSchemaName(effectiveTenant);

        Connection connection = dataSource.getConnection();

        try (Statement stmt = connection.createStatement()) {

            if (!DEFAULT_SCHEMA.equals(effectiveTenant)) {
                ensureSchemaExists(connection, effectiveTenant);

                String quotedTenant = quoteIdentifier(effectiveTenant);

                String setSearchPath = "SET search_path TO " + quotedTenant + ", public";
                log.info("🎯 [MT] getConnection | thread={} | tenantParam={} | tenantThread={} | SQL={}",
                        threadId, tenantIdentifier, threadTenant, setSearchPath);

                stmt.execute(setSearchPath);

            } else {
                log.info("🏠 [MT] getConnection | thread={} | tenantParam={} | tenantThread={} | SQL=SET search_path TO public;",
                        threadId, tenantIdentifier, threadTenant);

                stmt.execute("SET search_path TO public;");
            }

            return connection;

        } catch (SQLException e) {
            log.error("❌ [MT] Erro configurando conexão | effectiveTenant={}", effectiveTenant, e);
            try { connection.close(); } catch (SQLException ignore) {}
            throw e;
        }
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        if (connection == null || connection.isClosed()) return;

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SET search_path TO public;");
        } finally {
            connection.close();
        }
    }

    private void ensureSchemaExists(Connection connection, String schemaName) throws SQLException {
        String quotedSchema = quoteIdentifier(schemaName);

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("CREATE SCHEMA IF NOT EXISTS " + quotedSchema);
        }

        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM information_schema.schemata WHERE schema_name = ?")) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Schema " + schemaName + " não encontrado após CREATE");
                }
            }
        }
    }

    private void validateSchemaName(String schemaName) {
        if (!StringUtils.hasText(schemaName)) {
            throw new IllegalArgumentException("schemaName vazio");
        }
        if (!schemaName.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("schemaName inválido: " + schemaName);
        }
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier + "\"";
    }
}
