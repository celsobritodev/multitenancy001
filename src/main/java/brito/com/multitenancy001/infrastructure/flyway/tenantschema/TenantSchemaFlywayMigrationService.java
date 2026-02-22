package brito.com.multitenancy001.infrastructure.flyway.tenantschema;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Serviço de infraestrutura responsável por aplicar migrations Flyway
 * em schemas de tenants.
 *
 * Responsabilidades:
 * - Configurar Flyway dinamicamente por schema.
 * - Executar migrations versionadas do tenant.
 * - Garantir que apenas migrations compatíveis sejam aplicadas.
 *
 * Regras:
 * - Não deve conter lógica de negócio.
 * - Atua exclusivamente na camada de infraestrutura.
 */
@Service
@RequiredArgsConstructor
public class TenantSchemaFlywayMigrationService {

    private final DataSource dataSource;

    public void migrateTenantSchema(String tenantSchema) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas(tenantSchema)
                .defaultSchema(tenantSchema)
                .createSchemas(false)
                .locations("classpath:db/migration/tenants")
                .baselineOnMigrate(true)
                .load();

        flyway.migrate();
    }
}
