package brito.com.multitenancy001.infrastructure.tenant;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.Objects;

import javax.sql.DataSource;

import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.stereotype.Service;

import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.provisioning.infra.TenantFlywayMigrator;
import lombok.RequiredArgsConstructor;

/**
 * Provisionamento de schema TENANT (Postgres):
 * - cria schema se não existir
 * - roda flyway do tenant
 *
 * ✅ Zero corrida: usa pg_advisory_lock e executa Flyway NA MESMA connection
 * ✅ Clean: sem sleep, sem "depende de timing"
 *
 * Também expõe checks (schemaExists/tableExists) para o TenantExecutor.
 */
@Service
@RequiredArgsConstructor
public class TenantSchemaProvisioningService {

    private static final Duration DEFAULT_LOCK_TIMEOUT = Duration.ofSeconds(30);

    private final DataSource dataSource;
    private final TenantFlywayMigrator tenantFlywayMigrator;

    // =========================================================
    // Provisioning
    // =========================================================

    /**
     * @return true se schema existe (criado agora ou já existia) e migração rodou com sucesso
     */
    public boolean ensureSchemaExistsAndMigrate(String schemaName) {
        validateSchemaName(schemaName);

        try (Connection conn = dataSource.getConnection()) {

            long lockKey = advisoryKey(schemaName);

            if (!tryAdvisoryLock(conn, lockKey, DEFAULT_LOCK_TIMEOUT)) {
                throw new ApiException(
                        "TENANT_SCHEMA_LOCK_TIMEOUT",
                        "Não foi possível obter lock de provisionamento do schema '" + schemaName + "'",
                        409
                );
            }

            try {
                createSchemaIfNotExists(conn, schemaName);

                // Flyway do tenant usando a MESMA connection (lock continua válido)
                var single = new SingleConnectionDataSource(conn, true);
                tenantFlywayMigrator.migrate(single, schemaName);

                return true;

            } finally {
                advisoryUnlock(conn, lockKey);
            }

        } catch (SQLException e) {
            throw new ApiException(
                    "TENANT_SCHEMA_PROVISIONING_FAILED",
                    "Falha ao provisionar schema '" + schemaName + "': " + e.getMessage(),
                    500
            );
        }
    }

    public void tryDropSchema(String schemaName) {
        validateSchemaName(schemaName);

        try (Connection conn = dataSource.getConnection()) {

            long lockKey = advisoryKey(schemaName);

            if (!tryAdvisoryLock(conn, lockKey, DEFAULT_LOCK_TIMEOUT)) {
                throw new ApiException(
                        "TENANT_SCHEMA_LOCK_TIMEOUT",
                        "Não foi possível obter lock para drop do schema '" + schemaName + "'",
                        409
                );
            }

            try {
                dropSchemaIfExists(conn, schemaName);
            } finally {
                advisoryUnlock(conn, lockKey);
            }

        } catch (SQLException e) {
            throw new ApiException(
                    "TENANT_SCHEMA_DROP_FAILED",
                    "Falha ao dropar schema '" + schemaName + "': " + e.getMessage(),
                    500
            );
        }
    }

    // =========================================================
    // Read-only checks (usados por TenantExecutor)
    // =========================================================

    public boolean schemaExists(String schemaName) {
        validateSchemaName(schemaName);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                 select exists (
                     select 1
                       from information_schema.schemata
                      where schema_name = ?
                 )
             """)) {

            ps.setString(1, schemaName.trim());

            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }

        } catch (SQLException e) {
            throw new ApiException(
                    "TENANT_SCHEMA_EXISTS_CHECK_FAILED",
                    "Falha ao verificar schema '" + schemaName + "': " + e.getMessage(),
                    500
            );
        }
    }

    /**
     * requiredTable: nome "cru" da tabela (sem schema), ex: "products"
     */
    public boolean tableExists(String schemaName, String tableName) {
        validateSchemaName(schemaName);

        if (tableName == null || tableName.isBlank()) {
            throw new ApiException("TABLE_REQUIRED", "requiredTable é obrigatório", 400);
        }

        String t = tableName.trim();

        // tabela geralmente é snake_case; liberamos underscore e números.
        if (!t.matches("^[a-z][a-z0-9_]*$")) {
            throw new ApiException(
                    "TABLE_INVALID",
                    "Nome de tabela inválido: " + t,
                    400
            );
        }

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                 select exists (
                     select 1
                       from information_schema.tables
                      where table_schema = ?
                        and table_name = ?
                 )
             """)) {

            ps.setString(1, schemaName.trim());
            ps.setString(2, t);

            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }

        } catch (SQLException e) {
            throw new ApiException(
                    "TENANT_TABLE_EXISTS_CHECK_FAILED",
                    "Falha ao verificar tabela '" + schemaName + "." + t + "': " + e.getMessage(),
                    500
            );
        }
    }

    // =========================================================
    // SQL helpers
    // =========================================================

    private void createSchemaIfNotExists(Connection conn, String schemaName) throws SQLException {
        String sql = "create schema if not exists " + schemaName;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.execute();
        }
    }

    private void dropSchemaIfExists(Connection conn, String schemaName) throws SQLException {
        String sql = "drop schema if exists " + schemaName + " cascade";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.execute();
        }
    }

    // =========================================================
    // Advisory lock helpers (Postgres)
    // =========================================================

    private boolean tryAdvisoryLock(Connection conn, long key, Duration timeout) throws SQLException {
        Objects.requireNonNull(timeout, "timeout");

        // sem atraso: tenta uma vez
        try (PreparedStatement ps = conn.prepareStatement("select pg_try_advisory_lock(?)")) {
            ps.setLong(1, key);
            try (var rs = ps.executeQuery()) {
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

    private long advisoryKey(String schemaName) {
        return fnv1a64(schemaName);
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

    private void validateSchemaName(String schemaName) {
        if (schemaName == null || schemaName.isBlank()) {
            throw new ApiException("SCHEMA_REQUIRED", "schemaName é obrigatório", 400);
        }

        String s = schemaName.trim();

        if (s.length() > 63) {
            throw new ApiException("SCHEMA_INVALID", "schemaName excede 63 caracteres", 400);
        }

        if (!s.matches("^[a-z][a-z0-9_]*$")) {
            throw new ApiException(
                    "SCHEMA_INVALID",
                    "schemaName inválido. Use apenas [a-z0-9_] e comece com letra. Ex: t_minha_loja_abcdef",
                    400
            );
        }
    }
}
