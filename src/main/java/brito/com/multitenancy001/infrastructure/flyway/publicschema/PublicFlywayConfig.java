package brito.com.multitenancy001.infrastructure.flyway.publicschema;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import brito.com.multitenancy001.shared.db.Schemas;

@Configuration
public class PublicFlywayConfig {

    @Bean
    public Flyway flywayPublic(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas(Schemas.CONTROL_PLANE)
                .locations("classpath:db/migration/accounts")
                .baselineOnMigrate(true)
                .load();

      
        return flyway;
    }
}
