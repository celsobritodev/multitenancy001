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

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.provisioning.infra.TenantFlywayMigrator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Provisionamento de schema tenant no Postgres.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Criar schema do tenant se não existir.</li>
 *   <li>Executar migrations Flyway do tenant na mesma conexão protegida por advisory lock.</li>
 *   <li>Executar verificações de existência de schema e tabela.</li>
 * </ul>
 *
 * <p>Garantias:</p>
 * <ul>
 *   <li>Zero corrida via {@code pg_advisory_lock}.</li>
 *   <li>Flyway executado na mesma conexão do lock.</li>
 *   <li>Respostas amigáveis ao cliente; detalhes técnicos ficam apenas no log.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantSchemaProvisioner {

    private static final Duration DEFAULT_LOCK_TIMEOUT = Duration.ofSeconds(30);

    private final DataSource dataSource;

    /**
     * Garante que o schema do tenant exista e aplica migrations.
     *
     * @param tenantSchema schema do tenant
     * @return true quando o schema estiver pronto
     */
    public boolean ensureSchemaExistsAndMigrate(String tenantSchema) {
        validateTenantSchema(tenantSchema);

        try (Connection conn = dataSource.getConnection()) {

            long lockKey = advisoryKey(tenantSchema);

            if (!tryAdvisoryLock(conn, lockKey, DEFAULT_LOCK_TIMEOUT)) {
                throw new ApiException(
                        ApiErrorCode.TENANT_SCHEMA_LOCK_TIMEOUT,
                        "Não foi possível obter lock de provisionamento do schema '" + tenantSchema + "'"
                );
            }

            try {
                log.info("🔄 Iniciando provisionamento do schema tenant | tenantSchema={}", tenantSchema);

                createSchemaIfNotExists(conn, tenantSchema);

                SingleConnectionDataSource single = new SingleConnectionDataSource(conn, true);
                try {
                    TenantFlywayMigrator.migrate(single, tenantSchema);
                } finally {
                    try {
                        single.destroy();
                    } catch (Exception ignored) {
                        log.debug("Falha best-effort ao destruir SingleConnectionDataSource | tenantSchema={}", tenantSchema);
                    }
                }

                log.info("✅ Provisionamento concluído com sucesso | tenantSchema={}", tenantSchema);
                return true;

            } finally {
                advisoryUnlock(conn, lockKey);
            }

        } catch (SQLException e) {
            log.error(
                    "❌ Falha SQL ao provisionar schema tenant | tenantSchema={} | message={}",
                    tenantSchema,
                    e.getMessage(),
                    e
            );
            throw new ApiException(
                    ApiErrorCode.TENANT_SCHEMA_PROVISIONING_FAILED,
                    "Falha interna ao provisionar schema do tenant."
            );
        }
    }

    /**
     * Tenta dropar o schema do tenant.
     *
     * @param tenantSchema schema do tenant
     */
    public void tryDropSchema(String tenantSchema) {
        validateTenantSchema(tenantSchema);

        try (Connection conn = dataSource.getConnection()) {

            long lockKey = advisoryKey(tenantSchema);

            if (!tryAdvisoryLock(conn, lockKey, DEFAULT_LOCK_TIMEOUT)) {
                throw new ApiException(
                        ApiErrorCode.TENANT_SCHEMA_LOCK_TIMEOUT,
                        "Não foi possível obter lock para drop do schema '" + tenantSchema + "'"
                );
            }

            try {
                log.info("🗑️ Iniciando drop de schema tenant | tenantSchema={}", tenantSchema);
                dropSchemaIfExists(conn, tenantSchema);
                log.info("✅ Drop de schema concluído | tenantSchema={}", tenantSchema);
            } finally {
                advisoryUnlock(conn, lockKey);
            }

        } catch (SQLException e) {
            log.error(
                    "❌ Falha SQL ao dropar schema tenant | tenantSchema={} | message={}",
                    tenantSchema,
                    e.getMessage(),
                    e
            );
            throw new ApiException(
                    ApiErrorCode.TENANT_SCHEMA_DROP_FAILED,
                    "Falha interna ao remover schema do tenant."
            );
        }
    }

    /**
     * Verifica se o schema do tenant existe.
     *
     * @param tenantSchema schema do tenant
     * @return true se existir
     */
    public boolean schemaExists(String tenantSchema) {
        validateTenantSchema(tenantSchema);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("""
                 select exists (
                     select 1
                       from information_schema.schemata
                      where schema_name = ?
                 )
             """)) {

            ps.setString(1, tenantSchema);

            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }

        } catch (SQLException e) {
            log.error(
                    "❌ Falha SQL ao verificar existência de schema | tenantSchema={} | message={}",
                    tenantSchema,
                    e.getMessage(),
                    e
            );
            throw new ApiException(
                    ApiErrorCode.TENANT_SCHEMA_EXISTS_CHECK_FAILED,
                    "Falha interna ao verificar schema do tenant."
            );
        }
    }

    /**
     * Verifica se a tabela existe no schema do tenant.
     *
     * @param tenantSchema schema do tenant
     * @param tableName nome da tabela
     * @return true se existir
     */
    public boolean tableExists(String tenantSchema, String tableName) {
        validateTenantSchema(tenantSchema);

        if (tableName == null || tableName.isBlank()) {
            throw new ApiException(ApiErrorCode.TABLE_REQUIRED, "requiredTable é obrigatório");
        }

        String t = tableName.trim();

        if (!t.matches("^[a-z][a-z0-9_]*$")) {
            throw new ApiException(ApiErrorCode.TABLE_INVALID, "Nome de tabela inválido: " + t);
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

            ps.setString(1, tenantSchema);
            ps.setString(2, t);

            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }

        } catch (SQLException e) {
            log.error(
                    "❌ Falha SQL ao verificar tabela do tenant | tenantSchema={} | table={} | message={}",
                    tenantSchema,
                    t,
                    e.getMessage(),
                    e
            );
            throw new ApiException(
                    ApiErrorCode.TENANT_TABLE_EXISTS_CHECK_FAILED,
                    "Falha interna ao verificar tabela do tenant."
            );
        }
    }

    /**
     * Cria schema se ainda não existir.
     *
     * @param conn conexão atual
     * @param tenantSchema schema do tenant
     * @throws SQLException erro SQL
     */
    private void createSchemaIfNotExists(Connection conn, String tenantSchema) throws SQLException {
        String sql = "create schema if not exists " + tenantSchema;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.execute();
        }
    }

    /**
     * Remove schema se existir.
     *
     * @param conn conexão atual
     * @param tenantSchema schema do tenant
     * @throws SQLException erro SQL
     */
    private void dropSchemaIfExists(Connection conn, String tenantSchema) throws SQLException {
        String sql = "drop schema if exists " + tenantSchema + " cascade";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.execute();
        }
    }

    /**
     * Tenta obter advisory lock do Postgres.
     *
     * @param conn conexão atual
     * @param key chave do lock
     * @param timeout timeout lógico
     * @return true se lock obtido
     * @throws SQLException erro SQL
     */
    private boolean tryAdvisoryLock(Connection conn, long key, Duration timeout) throws SQLException {
        Objects.requireNonNull(timeout, "timeout");

        try (PreparedStatement ps = conn.prepareStatement("select pg_try_advisory_lock(?)")) {
            ps.setLong(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getBoolean(1);
            }
        }
    }

    /**
     * Libera advisory lock.
     *
     * @param conn conexão atual
     * @param key chave do lock
     */
    private void advisoryUnlock(Connection conn, long key) {
        try (PreparedStatement ps = conn.prepareStatement("select pg_advisory_unlock(?)")) {
            ps.setLong(1, key);
            ps.execute();
        } catch (SQLException ignored) {
            log.debug("Falha best-effort ao liberar advisory lock | key={}", key);
        }
    }

    /**
     * Calcula a chave de advisory lock para o schema.
     *
     * @param tenantSchema schema do tenant
     * @return chave de lock
     */
    private long advisoryKey(String tenantSchema) {
        return fnv1a64(tenantSchema);
    }

    /**
     * Calcula hash FNV-1a 64 bits.
     *
     * @param s string base
     * @return hash calculado
     */
    private long fnv1a64(String s) {
        long hash = 0xcbf29ce484222325L;
        for (int i = 0; i < s.length(); i++) {
            hash ^= s.charAt(i);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    /**
     * Valida nome do schema do tenant.
     *
     * @param tenantSchema schema do tenant
     */
    private void validateTenantSchema(String tenantSchema) {
        if (tenantSchema == null || tenantSchema.isBlank()) {
            throw new ApiException(ApiErrorCode.SCHEMA_REQUIRED, "tenantSchema é obrigatório");
        }

        String s = tenantSchema.trim();

        if (s.length() > 63) {
            throw new ApiException(ApiErrorCode.SCHEMA_INVALID, "tenantSchema excede 63 caracteres");
        }

        if (!s.matches("^[a-z][a-z0-9_]*$")) {
            throw new ApiException(
                    ApiErrorCode.SCHEMA_INVALID,
                    "tenantSchema inválido. Use apenas [a-z0-9_] e comece com letra. Ex: t_minha_loja_abcdef"
            );
        }
    }
}