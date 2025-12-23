package brito.com.multitenancy001.configuration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.engine.jdbc.connections.spi.AbstractDataSourceBasedMultiTenantConnectionProviderImpl;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaMultiTenantConnectionProvider
        extends AbstractDataSourceBasedMultiTenantConnectionProviderImpl<String> {

    private static final long serialVersionUID = 1L;
    private static final String DEFAULT_TENANT = "public";

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
        String threadTenant = CurrentTenantIdentifierResolverImpl.resolveBoundTenantOrDefault();


        // 🔥 REGRA ABSOLUTA: tenantIdentifier manda; se vier vazio, volta pro public
        String effectiveTenant = StringUtils.hasText(tenantIdentifier)
                ? tenantIdentifier
                : DEFAULT_TENANT;

        if (!StringUtils.hasText(tenantIdentifier)) {
            log.warn("⚠️ [MT] tenantIdentifier vazio → usando DEFAULT ({}) | threadTenant={}",
                    DEFAULT_TENANT, threadTenant);
        }

        // ✅ valida o schema antes de usar em SQL (evita injection e nomes inválidos)
        validateSchemaName(effectiveTenant);

        Connection connection = dataSource.getConnection();

        try (Statement stmt = connection.createStatement()) {

            if (!DEFAULT_TENANT.equals(effectiveTenant)) {

                ensureSchemaExists(connection, effectiveTenant);

                // ✅ quote do identificador: "tenant_xxx"
                String quotedTenant = quoteIdentifier(effectiveTenant);

                // Importante: manter public no search_path também
                String setSearchPath = "SET search_path TO " + quotedTenant + ", public";
                log.info("🎯 [MT] getConnection | thread={} | tenantParam={} | tenantThread={} | SQL={}",
                        threadId, tenantIdentifier, threadTenant, setSearchPath);

                stmt.execute(setSearchPath);

            } else {

                log.info("🏠 [MT] getConnection | thread={} | tenantParam={} | tenantThread={} | SQL=SET search_path TO public",
                        threadId, tenantIdentifier, threadTenant);

                stmt.execute("SET search_path TO public");
            }

            // 🔎 log final (debug)
            if (log.isDebugEnabled()) {
                try (ResultSet rs = stmt.executeQuery("SHOW search_path")) {
                    if (rs.next()) {
                        log.debug("🧪 [MT] search_path atual = {}", rs.getString(1));
                    }
                }
            }

            return connection;

        } catch (SQLException e) {
            log.error("❌ [MT] Erro configurando conexão | effectiveTenant={}", effectiveTenant, e);
            try {
                connection.close();
            } catch (SQLException closeEx) {
                log.warn("⚠️ [MT] Falha ao fechar conexão após erro", closeEx);
            }
            throw e;
        }
    }

    /**
     * 🔥 Garante que o schema existe (idempotente) - sem SQL injection
     */
    private void ensureSchemaExists(Connection connection, String schemaName) throws SQLException {

        // schemaName já foi validado, então podemos quotar com segurança
        String quotedSchema = quoteIdentifier(schemaName);

        // CREATE SCHEMA IF NOT EXISTS "tenant_xxx"
        String sql = "CREATE SCHEMA IF NOT EXISTS " + quotedSchema;

        try (Statement stmt = connection.createStatement()) {
            log.info("📦 [MT] ensureSchemaExists | {}", sql);
            stmt.execute(sql);
        }

        // checagem com PreparedStatement
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT 1 FROM information_schema.schemata WHERE schema_name = ?")) {
            ps.setString(1, schemaName);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    log.info("✅ [MT] Schema {} pronto", schemaName);
                } else {
                    log.error("❌ [MT] Schema {} NÃO encontrado após CREATE", schemaName);
                }
            }
        }
    }

    /**
     * ✅ ESSENCIAL: resetar search_path antes de devolver a conexão ao pool
     * Isso evita "vazamento" de schema entre requests.
     */
    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        if (connection == null || connection.isClosed()) return;

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("SET search_path TO public");
            if (log.isDebugEnabled()) {
                try (ResultSet rs = stmt.executeQuery("SHOW search_path")) {
                    if (rs.next()) {
                        log.debug("🔁 [MT] releaseConnection reset search_path = {}", rs.getString(1));
                    }
                }
            }
        } catch (SQLException e) {
            // mesmo se der erro no reset, vamos fechar para não contaminar pool
            log.warn("⚠️ [MT] Falha ao resetar search_path no releaseConnection (fechando mesmo assim). tenant={}",
                    tenantIdentifier, e);
        } finally {
            log.debug("🔌 [MT] Liberando conexão | tenantParam={}", tenantIdentifier);
            connection.close();
        }
    }

    /**
     * Valida schemaName para evitar injection e nomes inválidos.
     * Regra: letras, números e underscore, começando com letra/underscore.
     */
    private void validateSchemaName(String schemaName) {
        if (!StringUtils.hasText(schemaName)) {
            throw new IllegalArgumentException("schemaName vazio");
        }
        if (!schemaName.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("schemaName inválido: " + schemaName);
        }
    }

    /**
     * Quote seguro para identificador Postgres: "schema"
     * (schemaName já validado, então não precisa escape complexo)
     */
    private String quoteIdentifier(String identifier) {
        return "\"" + identifier + "\"";
    }
}
