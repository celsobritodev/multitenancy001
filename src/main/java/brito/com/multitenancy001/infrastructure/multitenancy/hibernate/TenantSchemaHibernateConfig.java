package brito.com.multitenancy001.infrastructure.multitenancy.hibernate;

import lombok.RequiredArgsConstructor;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.MultiTenancySettings;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.hibernate5.SpringBeanContainer;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class TenantSchemaHibernateConfig {

    private final DataSource dataSource;
    private final TenantSchemaConnectionProvider tenantSchemaConnectionProvider;
    private final CurrentTenantSchemaResolver currentTenantSchemaResolver;

    private final ConfigurableListableBeanFactory configurableListableBeanFactory;

    @Bean(name = "tenantEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean tenantEntityManagerFactory() {
        var emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(dataSource);

        // Entidades do TENANT
        emf.setPackagesToScan("brito.com.multitenancy001.tenant.domain");

        emf.setPersistenceUnitName("TENANT_PU");
        emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

        Map<String, Object> props = new HashMap<>();
        props.put("hibernate.hbm2ddl.auto", "none");
        props.put("hibernate.show_sql", true);
        props.put("hibernate.format_sql", true);
     


        // ✅ Multi-tenancy por schema (strategy)
        // (Não use AvailableSettings.MULTI_TENANT — não existe no Hibernate 6)
        props.put("hibernate.multiTenancy", "SCHEMA");

        // ✅ Provider/Resolver (use constantes do Hibernate)
        props.put(MultiTenancySettings.MULTI_TENANT_CONNECTION_PROVIDER, tenantSchemaConnectionProvider);
        props.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, currentTenantSchemaResolver);

        // ✅ Hibernate resolve beans do Spring (EntityListeners @Component etc.)
        props.put(AvailableSettings.BEAN_CONTAINER, new SpringBeanContainer(configurableListableBeanFactory));

        emf.setJpaPropertyMap(props);
        return emf;
    }

    @Bean(name = "tenantTransactionManager")
    public PlatformTransactionManager tenantTransactionManager(
            @Qualifier("tenantEntityManagerFactory") jakarta.persistence.EntityManagerFactory emf
    ) {
        return new JpaTransactionManager(emf);
    }
}
