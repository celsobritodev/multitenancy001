package brito.com.multitenancy001.infrastructure.persistence.multitenancy.hibernate;

import lombok.RequiredArgsConstructor;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.MultiTenancySettings;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.orm.hibernate5.SpringBeanContainer;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuração do EntityManagerFactory TENANT usando Hibernate multi-tenancy por SCHEMA.
 *
 * <p>Regras do projeto:</p>
 * <ul>
 *   <li>Entidades TENANT vivem em {@code brito.com.multitenancy001.tenant.*}</li>
 *   <li>O tenant é resolvido por schema via {@link TenantSchemaResolver}</li>
 *   <li>Conexões são fornecidas por {@link TenantSchemaConnectionProvider}</li>
 *   <li>O TransactionManager do TENANT é {@link JpaTransactionManager}</li>
 * </ul>
 */
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@Configuration
@RequiredArgsConstructor
public class TenantSchemaHibernateConfig {

    private final DataSource dataSource;
    private final TenantSchemaConnectionProvider tenantSchemaConnectionProvider;
    private final TenantSchemaResolver currentTenantSchemaResolver;
    private final ConfigurableListableBeanFactory configurableListableBeanFactory;

    @Bean(name = "tenantEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean tenantEntityManagerFactory() {
        LocalContainerEntityManagerFactoryBean emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(dataSource);

        /**
         * Entidades do TENANT (módulo-first: tenant.users/products/categories/etc.)
         */
        emf.setPackagesToScan("brito.com.multitenancy001.tenant");

        emf.setPersistenceUnitName("TENANT_PU");
        emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

        Map<String, Object> props = new HashMap<>();

        // DDL sempre via Flyway (você dropa o banco e recria)
        props.put(AvailableSettings.HBM2DDL_AUTO, "none");

        // Log SQL (mantenho como você está usando)
        props.put(AvailableSettings.SHOW_SQL, true);
        props.put(AvailableSettings.FORMAT_SQL, true);

        /**
         * ✅ Multi-tenancy por SCHEMA
         *
         * Obs: Na sua versão, AvailableSettings.MULTI_TENANT não existe.
         * O key compatível é "hibernate.multiTenancy" (string).
         */
        props.put("hibernate.multiTenancy", "SCHEMA");

        // ✅ Provider/Resolver (constantes oficiais)
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