package brito.com.multitenancy001.infrastructure.persistence;

import brito.com.multitenancy001.shared.db.Schemas;
import lombok.RequiredArgsConstructor;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.hibernate5.SpringBeanContainer;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class PublicSchemaHibernateConfig {

    private final DataSource dataSource;
    private final ConfigurableListableBeanFactory configurableListableBeanFactory;

    @Bean(name = "entityManagerFactory")
    @Primary
    public LocalContainerEntityManagerFactoryBean entityManagerFactory() {

        var emf = new LocalContainerEntityManagerFactoryBean();
        emf.setDataSource(dataSource);

        /**
         * ✅ Entidades do PUBLIC (ControlPlane + shared public entities)
         * Antes você escaneava só controlplane.domain, então TenantLoginChallenge não era "managed type".
         */
        emf.setPackagesToScan(
                "brito.com.multitenancy001.controlplane.domain",
                "brito.com.multitenancy001.shared.persistence" // ✅ inclui shared.persistence.auth.TenantLoginChallenge
        );

        emf.setPersistenceUnitName("PUBLIC_PU");
        emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

        Map<String, Object> props = new HashMap<>();
        props.put(AvailableSettings.DEFAULT_SCHEMA, Schemas.CONTROL_PLANE);

        // Bean container para injeção em listeners/converters/etc
        props.put(AvailableSettings.BEAN_CONTAINER, new SpringBeanContainer(configurableListableBeanFactory));

        emf.setJpaPropertyMap(props);

        return emf;
    }
}
