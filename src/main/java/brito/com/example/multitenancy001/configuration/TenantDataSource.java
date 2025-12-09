package brito.com.example.multitenancy001.configuration;



import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import com.zaxxer.hikari.HikariDataSource;

@Configuration
public class TenantDataSource {
    
    @Value("${spring.datasource.url}")
    private String jdbcUrl;
    
    @Value("${spring.datasource.username}")
    private String username;
    
    @Value("${spring.datasource.password}")
    private String password;
    
    @Bean
    @Primary
    public DataSource dataSource() {
        AbstractRoutingDataSource dataSource = new AbstractRoutingDataSource() {
            @Override
            protected Object determineCurrentLookupKey() {
                return TenantContext.getCurrentTenant();
            }
        };
        
        Map<Object, Object> targetDataSources = new HashMap<>();
        DataSource masterDataSource = masterDataSource();
        targetDataSources.put("master", masterDataSource);
        
        // Adicione outros tenants aqui se necessário
        
        dataSource.setTargetDataSources(targetDataSources);
        dataSource.setDefaultTargetDataSource(masterDataSource);
        dataSource.afterPropertiesSet();
        
        return dataSource;
    }
    
    @Bean
    public DataSource masterDataSource() {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(jdbcUrl);
        dataSource.setUsername(username);
        dataSource.setPassword(password);  // ← AGORA USA A SENHA CORRETA!
        dataSource.setDriverClassName("org.postgresql.Driver");
        
        // Configurações HikariCP (opcionais)
        dataSource.setMaximumPoolSize(10);
        dataSource.setMinimumIdle(2);
        dataSource.setIdleTimeout(300000);
        dataSource.setConnectionTimeout(20000);
        dataSource.setMaxLifetime(1200000);
        
        return dataSource;
    }
}