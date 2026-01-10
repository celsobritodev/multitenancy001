package brito.com.multitenancy001.infrastructure.multitenancy.hibernate;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class TenantSchemaHibernateConfig {

    private final DataSource dataSource;
    private final TenantSchemaConnectionProvider multiTenantConnectionProvider;
    private final CurrentTenantSchemaResolver tenantResolver;

    @Bean(name = "publicEntityManagerFactory")
    @Primary
    public LocalContainerEntityManagerFactoryBean publicEntityManagerFactory() {
        var emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(dataSource);
        emf.setPackagesToScan("brito.com.multitenancy001.controlplane.domain");
        emf.setPersistenceUnitName("PUBLIC_PU");
        emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

        Map<String, Object> props = new HashMap<>();

        props.put("hibernate.hbm2ddl.auto", "none");
        props.put("hibernate.show_sql", true);
        props.put("hibernate.format_sql", true);
        props.put("hibernate.default_schema", "public");

        emf.setJpaPropertyMap(props);
        return emf;
    }

    @Bean(name = "tenantEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean tenantEntityManagerFactory() {
        var emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(dataSource);

        // ✅ aqui:
        emf.setPackagesToScan("brito.com.multitenancy001.tenant.domain");
        // (ou "brito.com.multitenancy001.tenant" se você quer pegar tudo do tenant)

        emf.setPersistenceUnitName("TENANT_PU");
        emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

        Map<String, Object> props = new HashMap<>();
        props.put("hibernate.hbm2ddl.auto", "none");
        props.put("hibernate.show_sql", true);
        props.put("hibernate.format_sql", true);

        props.put("hibernate.multiTenancy", "SCHEMA");
        props.put("hibernate.multi_tenant_connection_provider", multiTenantConnectionProvider);
        props.put("hibernate.tenant_identifier_resolver", tenantResolver);

        emf.setJpaPropertyMap(props);
        return emf;
    }

}
