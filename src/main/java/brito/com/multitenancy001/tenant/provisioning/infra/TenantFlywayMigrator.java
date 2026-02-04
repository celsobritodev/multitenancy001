package brito.com.multitenancy001.tenant.provisioning.infra;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Flyway para TENANT schemas (Opção A).
 *
 * ✅ NÃO se chama "flyway" -> não roda no bootstrap
 * ✅ você chama migrate(schema) quando provisionar o tenant
 * ✅ cada tenant tem seu flyway_schema_history NO PRÓPRIO schema
 *
 * Importante:
 * - defaultSchema = schemaName garante history e migrations “dentro” do tenant
 * - locations aponta para db/migration/tenants (como no seu projeto)
 */
@Component
@RequiredArgsConstructor
public class TenantFlywayMigrator {

    private final DataSource dataSource;

    public void migrate(String schemaName) {
        migrate(this.dataSource, schemaName);
    }

    /**
     * Permite passar um DataSource custom (ex: SingleConnectionDataSource)
     * para manter advisory lock e flyway na MESMA connection.
     */
    public void migrate(DataSource customDataSource, String schemaName) {
        validateSchemaName(schemaName);

        Flyway.configure()
                .dataSource(customDataSource)
                .schemas(schemaName)
                .defaultSchema(schemaName)
                .locations("classpath:db/migration/tenants")
                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .cleanDisabled(true)
                // ✅ garante que Flyway use o schema do tenant como alvo principal
                .createSchemas(false) // schema é criado pelo provisioning service (idempotente)
                .load()
                .migrate();
    }

    private static void validateSchemaName(String schemaName) {
        if (schemaName == null || schemaName.isBlank()) {
            throw new IllegalArgumentException("schemaName é obrigatório");
        }
        String s = schemaName.trim();
        if (s.length() > 63) {
            throw new IllegalArgumentException("schemaName excede 63 caracteres");
        }
        // seu padrão: t_<slug>_<shortId> (lowercase + underscore)
        if (!s.matches("^[a-z][a-z0-9_]*$")) {
            throw new IllegalArgumentException(
                    "schemaName inválido. Use apenas [a-z0-9_] e comece com letra. Ex: t_minha_loja_abcdef"
            );
        }
    }
}
