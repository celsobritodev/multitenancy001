package brito.com.multitenancy001.configuration;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayAccountConfig {

    @Bean
    public Flyway flywayAccount(DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas("public")
                .locations("classpath:db/migration/accounts")
                .baselineOnMigrate(true)
                .load();

        // 🚀 EXECUTA NA INICIALIZAÇÃO
        //flyway.migrate();

        return flyway;
    }
}
