package brito.com.multitenancy001.configuration;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class HibernateMultitenancyConfig {

    private final DataSource dataSource;
    private final SchemaMultiTenantConnectionProvider multiTenantConnectionProvider;
    private final CurrentTenantIdentifierResolverImpl tenantResolver;

    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory() {
        LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();

        emf.setDataSource(dataSource);
        // CORREÇÃO: ajuste o pacote para seu projeto real
        emf.setPackagesToScan("brito.com.multitenancy001"); 
        
        JpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        emf.setJpaVendorAdapter(vendorAdapter);

        Map<String, Object> props = new HashMap<>();
        
        // CORREÇÃO 1: Use a constante correta do Hibernate
        // Dependendo da versão do Hibernate, pode ser:
        // - "org.hibernate.cfg.Environment.MULTI_TENANT" (versões mais novas)
        // - "hibernate.multiTenancy" (versões mais antigas)
        
        // Para Hibernate 5.x/6.x use:
        props.put("hibernate.multiTenancy", "SCHEMA");
        
        // OU alternativa: usar a classe Environment
        // props.put(org.hibernate.cfg.Environment.MULTI_TENANT, 
        //           org.hibernate.MultiTenancyStrategy.SCHEMA);
        
        props.put("hibernate.multi_tenant_connection_provider", multiTenantConnectionProvider);
        props.put("hibernate.tenant_identifier_resolver", tenantResolver);
        props.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        props.put("hibernate.show_sql", true);
        props.put("hibernate.format_sql", true);
        
        // IMPORTANTE: Desabilitar criação automática de schema
        props.put("hibernate.hbm2ddl.auto", "none");
        

        emf.setJpaPropertyMap(props);

        return emf;
    }
}