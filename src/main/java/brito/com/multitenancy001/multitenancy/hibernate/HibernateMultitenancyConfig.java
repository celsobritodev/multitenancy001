package brito.com.multitenancy001.multitenancy.hibernate;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
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

    /**
     * 🔵 EMF para PUBLIC (plataforma) - SEM multi-tenancy
     * Usado para: accounts, platform_users, payments
     */
    @Bean(name = "publicEntityManagerFactory")
    @Primary
    public LocalContainerEntityManagerFactoryBean publicEntityManagerFactory() {
        LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();

        emf.setDataSource(dataSource);
        emf.setPackagesToScan(
            "brito.com.multitenancy001.platform.domain",
            "brito.com.multitenancy001.platform.domain.tenant",
            "brito.com.multitenancy001.platform.domain.user",
            "brito.com.multitenancy001.platform.domain.billing"
        );
        emf.setPersistenceUnitName("PUBLIC_PU");
        
        JpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        emf.setJpaVendorAdapter(vendorAdapter);

        Map<String, Object> props = new HashMap<>();
        
        // 🔵 SEM multi-tenancy
        props.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        props.put("hibernate.show_sql", true);
        props.put("hibernate.format_sql", true);
        props.put("hibernate.hbm2ddl.auto", "none");
        
        // Schema fixo para public
        props.put("hibernate.default_schema", "public");
        
        // Otimizações para queries da plataforma
        props.put("hibernate.jdbc.batch_size", 20);
        props.put("hibernate.order_inserts", true);
        props.put("hibernate.order_updates", true);

        emf.setJpaPropertyMap(props);

        return emf;
    }

    /**
     * 🟢 EMF para TENANTS - COM multi-tenancy
     * Usado para: tenant_users, products, categories, suppliers, sales
     */
    @Bean(name = "tenantEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean tenantEntityManagerFactory() {
        LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();

        emf.setDataSource(dataSource);
        emf.setPackagesToScan(
            "brito.com.multitenancy001.entities.tenant",
            "brito.com.multitenancy001.tenant.domain"
        );
        emf.setPersistenceUnitName("TENANT_PU");
        
        JpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        emf.setJpaVendorAdapter(vendorAdapter);

        Map<String, Object> props = new HashMap<>();
        
        // 🟢 COM multi-tenancy
        props.put("hibernate.multi_tenant", "SCHEMA");
        props.put("hibernate.multi_tenant_connection_provider", multiTenantConnectionProvider);
        props.put("hibernate.tenant_identifier_resolver", tenantResolver);
        
        props.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        props.put("hibernate.show_sql", true);
        props.put("hibernate.format_sql", true);
        props.put("hibernate.hbm2ddl.auto", "none");
        
        // Otimizações para queries multi-tenant
        props.put("hibernate.jdbc.batch_size", 15);
        props.put("hibernate.order_inserts", true);
        props.put("hibernate.order_updates", true);
        props.put("hibernate.query.fail_on_pagination_over_collection_fetch", true);

        emf.setJpaPropertyMap(props);

        return emf;
    }

    /**
     * 🔄 EMF FALLBACK (opcional) - para compatibilidade com código existente
     * Delega para o EMF apropriado baseado no contexto
     */
    @Bean(name = "entityManagerFactory")
    public LocalContainerEntityManagerFactoryBean entityManagerFactory() {
        // Retorna o EMF de tenant como fallback para código legado
        return tenantEntityManagerFactory();
    }
}