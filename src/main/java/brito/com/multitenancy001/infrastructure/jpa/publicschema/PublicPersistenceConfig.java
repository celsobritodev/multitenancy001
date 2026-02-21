package brito.com.multitenancy001.infrastructure.jpa.publicschema;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Wiring de repositories do schema PUBLIC (Control Plane).
 *
 * Regras:
 * - O EntityManagerFactory default do Spring Boot é o bean "entityManagerFactory" (PUBLIC).
 * - "publicEntityManagerFactory" é APENAS um alias semântico (não pode ser @Primary).
 * - O único @Primary do PUBLIC deve ficar no bean "entityManagerFactory" (config Hibernate).
 *
 * Motivação:
 * - Evitar ambiguidade de injeção de EntityManager quando existem múltiplos EMFs (public + tenant).
 */
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@Configuration
@EnableJpaRepositories(
        basePackages = {
                "brito.com.multitenancy001.controlplane.accounts.persistence",
                "brito.com.multitenancy001.controlplane.users.persistence",
                "brito.com.multitenancy001.controlplane.billing.persistence",
                "brito.com.multitenancy001.controlplane.scheduling.persistence",
                "brito.com.multitenancy001.infrastructure.publicschema"
        },
        entityManagerFactoryRef = "publicEntityManagerFactory",
        transactionManagerRef = "publicTransactionManager"
)
public class PublicPersistenceConfig {

    @Bean(name = "publicEntityManagerFactory")
    public EntityManagerFactory publicEntityManagerFactory(
            @Qualifier("entityManagerFactory") EntityManagerFactory emf
    ) {
        /* Alias semântico para o EMF público (não usar @Primary aqui). */
        return emf;
    }

    @Bean(name = "publicTransactionManager")
    public PlatformTransactionManager publicTransactionManager(
            @Qualifier("publicEntityManagerFactory") EntityManagerFactory emf
    ) {
        /* TransactionManager do PUBLIC (não usar @Primary aqui). */
        return new JpaTransactionManager(emf);
    }
}