package brito.com.multitenancy001.infrastructure.multitenancy.hibernate;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Persistência do schema PUBLIC (ControlPlane).
 * Explicita o que o Spring Boot criaria como default.
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "brito.com.multitenancy001.controlplane",
        entityManagerFactoryRef = "publicEntityManagerFactory",
        transactionManagerRef = "publicTransactionManager"
)
public class PublicPersistenceConfig {

    /**
     * Alias semântico do EntityManagerFactory default do Spring Boot.
     */
    @Bean(name = "publicEntityManagerFactory")
    @Primary
    public EntityManagerFactory publicEntityManagerFactory(
            @Qualifier("entityManagerFactory") EntityManagerFactory emf
    ) {
        return emf;
    }

    @Bean(name = "publicTransactionManager")
    @Primary
    public PlatformTransactionManager publicTransactionManager(
            @Qualifier("publicEntityManagerFactory") EntityManagerFactory emf
    ) {
        return new JpaTransactionManager(emf);
    }
}
