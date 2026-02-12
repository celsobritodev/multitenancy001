package brito.com.multitenancy001.infrastructure.flyway.tenantschema;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/**
 * Linguagem ubíqua:
 * - tenantSchema = identificador persistido do schema do tenant (accounts.schema_name)
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
