package brito.com.multitenancy001.tenant.provisioning.infra;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Flyway para TENANT schemas.
 *
 * ✅ NÃO se chama "flyway" -> não roda no bootstrap
 * ✅ você chama migrate(schema) quando provisionar o tenant
 * ✅ cada tenant tem seu schema history dentro do schema do tenant
 *
 * BLINDAGEM EXTRA (recomendado):
 * - table("tenant_flyway_schema_history") separa o histórico do PUBLIC
 */
@Component
@RequiredArgsConstructor
public class TenantFlywayMigrator {

    private final DataSource dataSource;

    public void migrate(String schemaName) {
        migrate(this.dataSource, schemaName);
    }

    public void migrate(DataSource customDataSource, String schemaName) {
        Flyway.configure()
                .dataSource(customDataSource)
                .schemas(schemaName)
                .defaultSchema(schemaName)

                // ✅ sua pasta real (pela sua lista)
                .locations("classpath:db/migration/tenants")

                // ✅ blindagem (opcional mas recomendado)
                .table("tenant_flyway_schema_history")

                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .cleanDisabled(true)
                .load()
                .migrate();
    }
}
