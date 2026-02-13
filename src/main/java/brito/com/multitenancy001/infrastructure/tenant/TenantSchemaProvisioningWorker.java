package brito.com.multitenancy001.infrastructure.tenant;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.provisioning.infra.TenantFlywayMigrator;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Objects;

/**
 * Provisionamento de schema TENANT (Postgres):
 * - cria schema se não existir
 * - roda flyway do tenant
 *
 * ✅ Race-safe: usa pg_advisory_lock e executa Flyway na MESMA connection
 * ✅ Determinístico: sem sleep / sem "depende de timing"
 *
 * Também expõe checks (schemaExists/tableExists) para o TenantExecutor.
 */
@Service
@RequiredArgsConstructor
public class TenantSchemaProvisioningWorker {

    private static final Duration DEFAULT_LOCK_TIMEOUT = Duration.ofSeconds(30);

    private final DataSource dataSource;

    // =========================================================
    // Provisioning
    // =========================================================

    /**
     * Cria schema (se necessário) e roda Flyway do tenant.
     *
     * Retorna true se executou (idempotente: pode rodar N vezes).
     */
    public boolean ensureSchemaExistsAndMigrate(String tenantSchema) {
        String s = validateAndNormalizeTenantSchema(tenantSchema);

        try (Connection conn = dataSource.getConnection()) {

            long lockKey = advisoryKey(s);

            if (!tryAdvisoryLock(conn, lockKey, DEFAULT_LOCK_TIMEOUT)) {
                throw new ApiException(
                        ApiErrorCode.TENANT_SCHEMA_LOCK_TIMEOUT,
                        "Não foi possível obter lock de provisionamento do schema '" + s + "'"
                );
            }

            try {
                createSchemaIfNotExists(conn, s);

                // Flyway do tenant usando a MESMA connection (lock continua válido)
                var single = new SingleConnectionDataSource(conn, true);
                TenantFlywayMigrator.migrate(single, s);

                return true;

            } finally {
                advisoryUnlock(conn, lockKey);
            }

        } catch (SQLException e) {
            throw new ApiException(
                    ApiErrorCode.TENANT_SCHEMA_PROVISIONING_FAILED,
                    "Falha ao provisionar schema '" + s + "': " + e.getMessage()
            );
        }
    }

    /**
     * Dropa schema do tenant (best-effort) com lock advisory.
     */
    public void tryDropSchema(String tenantSchema) {
        String s = validateAndNormalizeTenantSchema(tenantSchema);

        try (Connection conn = dataSource.getConnection()) {

            long lockKey = advisoryKey(s);

            if (!tryAdvisoryLock(conn, lockKey, DEFAULT_LOCK_TIMEOUT)) {
                throw new ApiException(
                        ApiErrorCode.TENANT_SCHEMA_LOCK_TIMEOUT,
                        "Não foi possível obter lock para drop do schema '" + s + "'"
                );
            }

            try {
                dropSchemaIfExists(conn, s);
            } finally {
                advisoryUnlock(conn, lockKey);
            }

        } catch (SQLException e) {
            throw new ApiException(
                    ApiErrorCode.TENANT_SCHEMA_DROP_FAILED,
                    "Falha ao dropar schema '" + s + "': " + e.getMessage()
            );
        }
    }

    // =========================================================
    // Read-only checks (usados por TenantExecutor)
    // =========================================================

    public boolean schemaExists(String tenantSchema) {
        String s = validateAndNormalizeTenantSchema(tenantSchema);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                 select exists (
                     select 1
                       from information_schema.schemata
                      where schema_name = ?
                 )
             """)) {

            ps.setString(1, s);

            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }

        } catch (SQLException e) {
            throw new ApiException(
                    ApiErrorCode.TENANT_SCHEMA_EXISTS_CHECK_FAILED,
                    "Falha ao verificar schema '" + s + "': " + e.getMessage()
            );
        }
    }

    /**
     * Verifica se uma tabela existe dentro de um schema específico.
     *
     * Observação:
     * - Aqui NÃO dependemos de TenantContext.
     * - É um check direto em information_schema.
     */
    public boolean tableExists(String tenantSchema, String tableName) {
        String schema = validateAndNormalizeTenantSchema(tenantSchema);
        String table = validateAndNormalizeTableName(tableName);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                 select exists (
                     select 1
                       from information_schema.tables
                      where table_schema = ?
                        and table_name = ?
                 )
             """)) {

            ps.setString(1, schema);
            ps.setString(2, table);

            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }

        } catch (SQLException e) {
            throw new ApiException(
                    ApiErrorCode.TENANT_TABLE_EXISTS_CHECK_FAILED,
                    "Falha ao verificar tabela '" + schema + "." + table + "': " + e.getMessage()
            );
        }
    }

    // =========================================================
    // SQL helpers
    // =========================================================

    /**
     * Schema identifier não pode ser parâmetro JDBC.
     * Então: valida (regex) e faz quote seguro.
     */
    private void createSchemaIfNotExists(Connection conn, String tenantSchema) throws SQLException {
        String sql = "create schema if not exists " + quoteIdent(tenantSchema);
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.execute();
        }
    }

    private void dropSchemaIfExists(Connection conn, String tenantSchema) throws SQLException {
        String sql = "drop schema if exists " + quoteIdent(tenantSchema) + " cascade";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.execute();
        }
    }

    /**
     * Quote simples e seguro para identifier já validado por regex [a-z][a-z0-9_]*
     * -> ainda assim colocamos aspas duplas para garantir.
     */
    private static String quoteIdent(String ident) {
        // ident já é validado (sem aspas, sem espaços), então isso é seguro.
        return "\"" + ident + "\"";
    }

    // =========================================================
    // Advisory lock helpers (Postgres)
    // =========================================================

    private boolean tryAdvisoryLock(Connection conn, long key, Duration timeout) throws SQLException {
        Objects.requireNonNull(conn, "conn");
        Objects.requireNonNull(timeout, "timeout");

        // comportamento determinístico: tenta uma vez (sem polling/sleep)
        try (PreparedStatement ps = conn.prepareStatement("select pg_try_advisory_lock(?)")) {
            ps.setLong(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }

    private void advisoryUnlock(Connection conn, long key) {
        try (PreparedStatement ps = conn.prepareStatement("select pg_advisory_unlock(?)")) {
            ps.setLong(1, key);
            ps.execute();
        } catch (SQLException ignored) {
            // best-effort
        }
    }

    private long advisoryKey(String tenantSchema) {
        return fnv1a64(tenantSchema);
    }

    private long fnv1a64(String s) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < s.length(); i++) {
            hash ^= s.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    // =========================================================
    // Validation
    // =========================================================

    private String validateAndNormalizeTenantSchema(String tenantSchema) {
        if (tenantSchema == null || tenantSchema.isBlank()) {
            throw new ApiException(ApiErrorCode.SCHEMA_REQUIRED, "tenantSchema é obrigatório");
        }

        String s = tenantSchema.trim();

        if (s.length() > 63) {
            throw new ApiException(ApiErrorCode.SCHEMA_INVALID, "tenantSchema excede 63 chars");
        }

        if (!s.matches("^[a-z][a-z0-9_]*$")) {
            throw new ApiException(
                    ApiErrorCode.SCHEMA_INVALID,
                    "tenantSchema inválido. Use [a-z0-9_] e comece com letra. Ex: t_minha_loja_abcdef"
            );
        }

        return s;
    }

    private String validateAndNormalizeTableName(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            throw new ApiException(ApiErrorCode.TABLE_REQUIRED, "tableName é obrigatório");
        }

        String t = tableName.trim();

        if (!t.matches("^[a-z][a-z0-9_]*$")) {
            throw new ApiException(ApiErrorCode.TABLE_INVALID, "tableName inválido");
        }

        return t;
    }
}
