package brito.com.multitenancy001.configuration;

import org.flywaydb.core.Flyway;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import javax.sql.DataSource;

@Configuration
public class FlywayConfig {

    @Bean
    public CommandLineRunner flywayAccountMigrate(DataSource dataSource) {
        return args -> {
            System.out.println("🚀 Inicializando Flyway para Accounts...");
            
            Flyway flywayAccount = Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration/accounts")
                    .schemas("public")
                    .baselineOnMigrate(true)
                    .baselineVersion("0")
                    .validateOnMigrate(true)
                    .load();

            // Executar migrações para accounts
            flywayAccount.migrate();
            
            System.out.println("✅ Migrações Flyway para Accounts executadas com sucesso!");
        };
    }

    @Bean
    public CommandLineRunner flywayTenantMigrate(DataSource dataSource) {
        return args -> {
            System.out.println("🚀 Inicializando Flyway para Tenants...");
            
            Flyway flywayTenant = Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration/tenants")
                    .schemas("public")
                    .baselineOnMigrate(true)
                    .baselineVersion("0")
                    .validateOnMigrate(true)
                    .load();

            // Executar migrações para tenants
            flywayTenant.migrate();
            
            System.out.println("✅ Migrações Flyway para Tenants executadas com sucesso!");
        };
    }
}
