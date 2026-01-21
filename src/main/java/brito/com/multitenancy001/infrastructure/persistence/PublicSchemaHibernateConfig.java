package brito.com.multitenancy001.infrastructure.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import brito.com.multitenancy001.shared.db.Schemas;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class PublicSchemaHibernateConfig {

    private final DataSource dataSource;

    /**
     * ✅ EMF "default" que o Spring Boot normalmente criaria.
     * Como você criou um EMF manual (tenantEntityManagerFactory),
     * o Boot recuou e NÃO criou mais o entityManagerFactory.
     *
     * Então criamos explicitamente aqui.
     */
    @Bean(name = "entityManagerFactory")
    @Primary
    public LocalContainerEntityManagerFactoryBean entityManagerFactory() {

        var emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(dataSource);

        // Entidades do PUBLIC (ControlPlane)
        emf.setPackagesToScan("brito.com.multitenancy001.controlplane.domain");

        emf.setPersistenceUnitName("PUBLIC_PU");
        emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

        Map<String, Object> props = new HashMap<>();
        props.put("hibernate.hbm2ddl.auto", "none");
        props.put("hibernate.show_sql", true);
        props.put("hibernate.format_sql", true);

        // opcional (Postgres): garantir que o default é public
        props.put("hibernate.default_schema", Schemas.CONTROL_PLANE);

        emf.setJpaPropertyMap(props);
        return emf;
    }
}
