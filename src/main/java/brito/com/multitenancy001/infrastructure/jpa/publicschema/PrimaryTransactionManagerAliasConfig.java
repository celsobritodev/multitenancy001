package brito.com.multitenancy001.infrastructure.jpa.publicschema;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Role;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Alias e "default" TransactionManager do Spring.
 *
 * <p>Por padrão, muitos pontos do ecossistema Spring (e libs) procuram um bean
 * chamado <b>"transactionManager"</b> (às vezes até por nome, não só por tipo).</p>
 *
 * <p>Neste projeto, a regra é:</p>
 * <ul>
 *   <li>Qualquer transação "genérica" (padrão) deve cair no PUBLIC schema (Control Plane).</li>
 *   <li>Tenant schema SEMPRE deve ser explícito (tenantTransactionManager via @TenantTx / TxExecutor / UnitOfWork).</li>
 * </ul>
 *
 * <p>Motivação:</p>
 * <ul>
 *   <li>Evitar que algum componente use um TransactionManager JDBC (DataSourceTransactionManager)
 *       e cause o erro "Pre-bound JDBC Connection found!" ao misturar com JPA.</li>
 *   <li>Tornar o comportamento previsível para quem depende do bean padrão por nome.</li>
 * </ul>
 */
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@Configuration
public class PrimaryTransactionManagerAliasConfig {

    /**
     * Bean padrão do Spring: "transactionManager".
     * Aponta explicitamente para o PUBLIC (JPA).
     */
    @Bean(name = "transactionManager")
    @Primary
    public PlatformTransactionManager transactionManager(
            @Qualifier("publicTransactionManager") PlatformTransactionManager publicTransactionManager
    ) {
        return publicTransactionManager;
    }
}