package brito.com.multitenancy001.infrastructure.flyway.publicschema;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationInitializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;

import brito.com.multitenancy001.shared.db.Schemas;

/**
 * Flyway do schema PUBLIC (Control Plane).
 *
 * ✅ Migra no bootstrap
 * ✅ Evita race com @Scheduled (use @DependsOn("flywayInitializer"))
 *
 * IMPORTANTE:
 * - bean "flyway" → usado pelo Spring Boot
 * - bean "flywayInitializer" → gatilho oficial de migração
 */
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@Configuration
public class PublicFlywayConfig {

    @Bean(name = "flyway")
    public Flyway flyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(Schemas.CONTROL_PLANE)       // "public"
                .defaultSchema(Schemas.CONTROL_PLANE)
                .locations("classpath:db/migration/accounts")
                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .cleanDisabled(true)
                .load();
    }

    @Bean(name = "flywayInitializer")
    public FlywayMigrationInitializer flywayInitializer(@Qualifier("flyway") Flyway flyway) {
        return new FlywayMigrationInitializer(flyway, null);
    }
}
