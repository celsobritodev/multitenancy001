package brito.com.multitenancy001.configuration;



import org.flywaydb.core.Flyway;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import javax.sql.DataSource;

@Configuration
public class FlywayConfig {
    
    @Bean
    public CommandLineRunner flywayMigrate(DataSource dataSource) {
        return args -> {
            System.out.println("🚀 Inicializando Flyway...");
            
            Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .schemas("public")
                    .baselineOnMigrate(true)
                    .baselineVersion("0")
                    .validateOnMigrate(true)
                    .load();
            
            // Executar migrações
            flyway.migrate();
            
            System.out.println("✅ Migrações Flyway executadas com sucesso!");
        };
    }
}