package brito.com.multitenancy001.infrastructure.flyway.tenantschema;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Linguagem ubíqua:
 * - Account.schemaName = identificador persistido do schema do tenant
 * - tenantSchema = o mesmo valor, usado como contexto de execução na infraestrutura
 */
@Service
@RequiredArgsConstructor
public class TenantSchemaFlywayMigrationService {

    private final DataSource dataSource;

    public void migrateTenantSchema(String tenantSchema) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas(tenantSchema)
                .defaultSchema(tenantSchema) // ESSENCIAL
                .createSchemas(false)
                .locations("classpath:db/migration/tenants")
                .baselineOnMigrate(true)
                .load();

        flyway.migrate();
    }
}
