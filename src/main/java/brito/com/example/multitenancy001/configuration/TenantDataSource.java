package brito.com.example.multitenancy001.configuration;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import com.zaxxer.hikari.HikariDataSource;







//TenantDataSource.java
@Configuration
public class TenantDataSource {
 
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
     
     dataSource.setTargetDataSources(targetDataSources);
     dataSource.setDefaultTargetDataSource(masterDataSource);
     dataSource.afterPropertiesSet();
     
     return dataSource;
 }
 
 @Bean
 public DataSource masterDataSource() {
     HikariDataSource dataSource = new HikariDataSource();
     dataSource.setJdbcUrl("jdbc:postgresql://localhost:5432/master_db");
     dataSource.setUsername("postgres");
     dataSource.setPassword("password");
     dataSource.setDriverClassName("org.postgresql.Driver");
     return dataSource;
 }
}