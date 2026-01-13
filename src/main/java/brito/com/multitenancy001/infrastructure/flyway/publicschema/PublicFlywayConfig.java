package brito.com.multitenancy001.infrastructure.flyway.publicschema;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PublicFlywayConfig {

    @Bean
    public Flyway flywayPublic(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas("public")
                .locations("classpath:db/migration/accounts")
                .baselineOnMigrate(true)
                .load();

      
        return flyway;
    }
}
