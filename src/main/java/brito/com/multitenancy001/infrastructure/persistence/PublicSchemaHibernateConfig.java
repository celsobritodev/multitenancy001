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

        // Entidades do PUBLIC (ControlPlane)
        emf.setPackagesToScan("brito.com.multitenancy001.controlplane.domain");

        emf.setPersistenceUnitName("PUBLIC_PU");
        emf.setJpaVendorAdapter(new HibernateJpaVendorAdapter());

        Map<String, Object> props = new HashMap<>();
        props.put("hibernate.hbm2ddl.auto", "none");
        props.put("hibernate.show_sql", true);
        props.put("hibernate.format_sql", true);

        // Default schema do PUBLIC (control plane)
        props.put("hibernate.default_schema", Schemas.CONTROL_PLANE);

        // ✅ Hibernate resolve beans gerenciados pelo Spring (EntityListeners @Component etc.)
        props.put(AvailableSettings.BEAN_CONTAINER, new SpringBeanContainer(configurableListableBeanFactory));

        emf.setJpaPropertyMap(props);
        return emf;
    }
}
