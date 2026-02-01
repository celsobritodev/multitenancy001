package brito.com.multitenancy001.infrastructure.tenant;

import brito.com.multitenancy001.infrastructure.flyway.tenantschema.TenantSchemaFlywayMigrationService;
import brito.com.multitenancy001.shared.db.Schemas;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantSchemaProvisioningService {

    private final JdbcTemplate jdbcTemplate;
    private final TenantSchemaFlywayMigrationService tenantSchemaFlywayMigrationService;

    private static final Pattern SCHEMA_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    private void validateSchemaNameOrThrow(String schemaName) {
        if (!StringUtils.hasText(schemaName)) {
            throw new ApiException("INVALID_SCHEMA", "SchemaName inválido", 400);
        }

        String trimmed = schemaName.trim();

        if (Schemas.CONTROL_PLANE.equalsIgnoreCase(trimmed)) {
            throw new ApiException("INVALID_SCHEMA",
                    "SchemaName '" + Schemas.CONTROL_PLANE + "' não é permitido",
                    400);
        }

        if (!SCHEMA_PATTERN.matcher(trimmed).matches()) {
            throw new ApiException(
                    "INVALID_SCHEMA",
                    "SchemaName inválido: use apenas letras, números e _ (underscore)",
                    400
            );
        }
    }

    public boolean schemaExists(String schemaName) {
        if (!StringUtils.hasText(schemaName)) return false;

        String normalized = schemaName.trim();

        if (Schemas.CONTROL_PLANE.equalsIgnoreCase(normalized)) return false;

        String sql = "SELECT EXISTS(SELECT 1 FROM information_schema.schemata WHERE schema_name = ?)";
        Boolean exists = jdbcTemplate.queryForObject(sql, Boolean.class, normalized);
        return Boolean.TRUE.equals(exists);
    }

    public boolean tableExists(String schemaName, String tableName) {
        if (!StringUtils.hasText(schemaName) || !StringUtils.hasText(tableName)) return false;

        String sql =
                "SELECT EXISTS(" +
                "  SELECT 1 FROM information_schema.tables " +
                "  WHERE table_schema = ? AND table_name = ?" +
                ")";
        Boolean exists = jdbcTemplate.queryForObject(sql, Boolean.class, schemaName, tableName);
        return Boolean.TRUE.equals(exists);
    }

    /**
     * Cria o schema (se necessário) e roda Flyway.
     * @return true se o schema foi criado agora por este fluxo (útil para compensação).
     */
    public boolean ensureSchemaExistsAndMigrate(String schemaName) {
        validateSchemaNameOrThrow(schemaName);

        String normalized = schemaName.trim().toLowerCase();

        boolean existedBefore = schemaExists(normalized);

        if (!existedBefore) {
            log.info("📦 Criando schemaName {}", normalized);
            jdbcTemplate.execute("CREATE SCHEMA \"" + normalized + "\"");
        }

        log.info("🧬 Rodando migrations do tenant: {}", normalized);
        tenantSchemaFlywayMigrationService.migrateTenantSchema(normalized);

        return !existedBefore;
    }

    /**
     * Compensação: tenta dropar schema CASCADE sem quebrar o erro original.
     * (Como você sempre dropa o banco, isso vai rodar só em falhas no onboarding).
     */
    public void tryDropSchema(String schemaName) {
        if (!StringUtils.hasText(schemaName)) return;

        String normalized = schemaName.trim().toLowerCase();

        try {
            log.warn("🧹 Compensação: drop schema {} cascade", normalized);
            jdbcTemplate.execute("DROP SCHEMA \"" + normalized + "\" CASCADE");
        } catch (Exception e) {
            log.error("⚠️ Falha ao dropar schema {} (ignorado)", normalized, e);
        }
    }
}
