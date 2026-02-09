package brito.com.multitenancy001.tenant.provisioning.infra;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Flyway para TENANT schemas.
 *
 * ✅ NÃO se chama "flyway" -> não roda no bootstrap
 * ✅ você chama migrate(tenantSchema) quando provisionar o tenant
 * ✅ cada tenant tem seu schema history dentro do schema do tenant
 *
 * BLINDAGEM EXTRA (recomendado):
 * - table("tenant_flyway_schema_history") separa o histórico do PUBLIC
 *
 * Linguagem ubíqua:
 * - tenantSchema = contexto de execução na infraestrutura
 */
@Component
@RequiredArgsConstructor
public class TenantFlywayMigrator {

    private static final String TENANT_HISTORY_TABLE = "tenant_flyway_schema_history";

    private final DataSource dataSource;

    public void migrate(String tenantSchema) {
        migrate(this.dataSource, tenantSchema);
    }

    /**
     * Executa Flyway usando o DataSource fornecido (pode ser SingleConnectionDataSource).
     * tenantSchema é o schema do tenant no contexto de execução.
     */
    public static void migrate(DataSource dataSource, String tenantSchema) {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(tenantSchema)
                .defaultSchema(tenantSchema)

                // ✅ sua pasta real (pela sua lista)
                .locations("classpath:db/migration/tenants")

                // ✅ blindagem (opcional mas recomendado)
                .table(TENANT_HISTORY_TABLE)

                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .cleanDisabled(true)
                .load()
                .migrate();
    }
}
