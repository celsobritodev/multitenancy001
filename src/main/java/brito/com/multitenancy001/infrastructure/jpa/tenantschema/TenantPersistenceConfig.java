package brito.com.multitenancy001.infrastructure.jpa.tenantschema;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Wiring dos repositories do schema TENANT.
 *
 * Observação:
 * - Seus repositories agora estão em tenant.<módulo>.persistence,
 *   então escaneamos "brito.com.multitenancy001.tenant" como raiz.
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "brito.com.multitenancy001.tenant",
        entityManagerFactoryRef = "tenantEntityManagerFactory",
        transactionManagerRef = "tenantTransactionManager"
)
public class TenantPersistenceConfig {
}
