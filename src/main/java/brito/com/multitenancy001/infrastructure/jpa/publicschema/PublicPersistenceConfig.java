package brito.com.multitenancy001.infrastructure.jpa.publicschema;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Wiring de repositories do schema PUBLIC (Control Plane).
 *
 * - Usa o EntityManagerFactory default do Spring Boot (bean: "entityManagerFactory")
 * - Cria um alias semântico: "publicEntityManagerFactory"
 *
 * IMPORTANTE:
 * - Repositórios do public schema agora ficam em:
 *   - controlplane.<módulo>.persistence
 *   - infrastructure.publicschema.* (ex.: auth/TenantLoginChallengeRepository)
 *
 * Não use mais:
 * - brito.com.multitenancy001.controlplane.persistence (não existe mais)
 * - brito.com.multitenancy001.shared.persistence (não existe mais)
 */
@Configuration
@EnableJpaRepositories(
        basePackages = {
                "brito.com.multitenancy001.controlplane.accounts.persistence",
                "brito.com.multitenancy001.controlplane.users.persistence",
                "brito.com.multitenancy001.controlplane.billing.persistence",
                // ✅ necessário para encontrar AccountJobScheduleRepository
                "brito.com.multitenancy001.controlplane.scheduling.persistence",

                "brito.com.multitenancy001.infrastructure.publicschema"
        },
        entityManagerFactoryRef = "publicEntityManagerFactory",
        transactionManagerRef = "publicTransactionManager"
)
public class PublicPersistenceConfig {

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
