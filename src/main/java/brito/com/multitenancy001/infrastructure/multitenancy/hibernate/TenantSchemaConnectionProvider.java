package brito.com.multitenancy001.infrastructure.multitenancy.hibernate;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.jdbc.connections.spi.AbstractDataSourceBasedMultiTenantConnectionProviderImpl;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.shared.context.TenantContext;
import brito.com.multitenancy001.shared.db.Schemas;

import javax.sql.DataSource;
import java.sql.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class TenantSchemaConnectionProvider
        extends AbstractDataSourceBasedMultiTenantConnectionProviderImpl<String> {

    private static final long serialVersionUID = 1L;

    /**
     * ✅ Default/root = Control Plane (hoje: "public")
     */
    private static final String DEFAULT_SCHEMA = Schemas.CONTROL_PLANE;

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

        // ✅ TenantContext guarda tenantSchema (null = PUBLIC). Aqui nunca devolve null.
        String threadTenantSchema = TenantContext.getOrDefaultPublic();

        // ✅ Hibernate pode passar vazio/null no root/public — isso é normal.
        String effectiveTenantSchema = StringUtils.hasText(tenantIdentifier)
                ? tenantIdentifier.trim()
                : DEFAULT_SCHEMA;

        // ✅ Log inteligente:
        // - tenantIdentifier vazio é NORMAL no root/public → DEBUG
        // - WARN só quando há divergência real entre tenantParam e tenantThread
        if (!StringUtils.hasText(tenantIdentifier)) {
            if (log.isDebugEnabled()) {
                log.debug("🏠 [MT] tenantParam vazio → usando DEFAULT ({}) | thread={} | tenantSchemaThread={}",
                        DEFAULT_SCHEMA, threadId, threadTenantSchema);
            }
        } else if (!effectiveTenantSchema.equals(threadTenantSchema)) {
            log.warn("⚠️ [MT] mismatch tenantSchemaParam vs tenantSchemaThread | thread={} | tenantSchemaParam={} | tenantSchemaThread={}",
                    threadId, effectiveTenantSchema, threadTenantSchema);
        }

        validateSchemaName(effectiveTenantSchema);

        Connection connection = dataSource.getConnection();

        try (Statement stmt = connection.createStatement()) {

            if (!DEFAULT_SCHEMA.equals(effectiveTenantSchema)) {
                ensureSchemaExists(connection, effectiveTenantSchema);

                String quotedTenant = quoteIdentifier(effectiveTenantSchema);
                String quotedDefault = quoteIdentifier(DEFAULT_SCHEMA);

                String setSearchPath = "SET search_path TO " + quotedTenant + ", " + quotedDefault;
                log.info("🎯 [MT] getConnection | thread={} | tenantSchemaParam={} | tenantSchemaThread={} | SQL={}",
                        threadId, effectiveTenantSchema, threadTenantSchema, setSearchPath);

                stmt.execute(setSearchPath);

            } else {
                String quotedDefault = quoteIdentifier(DEFAULT_SCHEMA);

                String setSearchPath = "SET search_path TO " + quotedDefault + ";";
                log.info("🏠 [MT] getConnection | thread={} | tenantSchemaParam={} | tenantSchemaThread={} | SQL={}",
                        threadId, effectiveTenantSchema, threadTenantSchema, setSearchPath);

                stmt.execute(setSearchPath);
            }

            return connection;

        } catch (SQLException e) {
            log.error("❌ [MT] Erro configurando conexão | effectiveTenantSchema={}", effectiveTenantSchema, e);
            try { connection.close(); } catch (SQLException ignore) {}
            throw e;
        }
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        long threadId = Thread.currentThread().threadId();

        if (connection == null) {
            if (log.isDebugEnabled()) {
                log.debug("🧹 [MT] releaseConnection ignorado (connection=null) | thread={} | tenantSchemaParam={}",
                        threadId, tenantIdentifier);
            }
            return;
        }

        if (connection.isClosed()) {
            if (log.isDebugEnabled()) {
                log.debug("🧹 [MT] releaseConnection ignorado (connection já fechada) | thread={} | tenantSchemaParam={}",
                        threadId, tenantIdentifier);
            }
            return;
        }

        try (Statement stmt = connection.createStatement()) {
            String resetSearchPath = "SET search_path TO " + quoteIdentifier(DEFAULT_SCHEMA) + ";";

            if (log.isDebugEnabled()) {
                log.debug("🧹 [MT] releaseConnection | thread={} | tenantSchemaParam={} | SQL={}",
                        threadId, tenantIdentifier, resetSearchPath);
            }

            stmt.execute(resetSearchPath);

        } catch (SQLException e) {
            log.warn("⚠️ [MT] Falha ao resetar search_path no releaseConnection | thread={} | tenantSchemaParam={}",
                    threadId, tenantIdentifier, e);
        } finally {
            connection.close();

            if (log.isDebugEnabled()) {
                log.debug("🔒 [MT] conexão fechada | thread={} | tenantSchemaParam={}", threadId, tenantIdentifier);
            }
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
        // Mantive simples igual ao seu, pois você já valida o regex seguro.
        return "\"" + identifier + "\"";
    }
}

