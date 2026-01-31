package brito.com.multitenancy001.tenant.provisioning.infra;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.stereotype.Component;

@Component
public class TenantFlywayMigrator {

    private final DataSource dataSource;

    public TenantFlywayMigrator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void migrate(String schemaName) {
        Flyway.configure()
                .dataSource(dataSource)
                .schemas(schemaName)
                .defaultSchema(schemaName)
                .locations("classpath:db/migration/tenant")
                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .load()
                .migrate();
    }
}
