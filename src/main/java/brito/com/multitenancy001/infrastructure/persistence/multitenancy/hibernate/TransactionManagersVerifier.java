package brito.com.multitenancy001.infrastructure.persistence.multitenancy.hibernate;

import lombok.RequiredArgsConstructor;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Verificador fail-fast para garantir que não exista TransactionManager JDBC
 * (DataSourceTransactionManager/JdbcTransactionManager) ativo de forma acidental.
 *
 * <p>Motivo:</p>
 * <ul>
 *   <li>Se algum fluxo abrir transação com TM JDBC e depois tentar usar JPA,
 *       ocorre: "Pre-bound JDBC Connection found! ... running within DataSourceTransactionManager".</li>
 * </ul>
 *
 * <p>Regra do projeto:</p>
 * <ul>
 *   <li>PUBLIC e TENANT usam JpaTransactionManager.</li>
 *   <li>TM JDBC é proibido neste projeto.</li>
 * </ul>
 */
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@Configuration
@RequiredArgsConstructor
public class TransactionManagersVerifier implements SmartInitializingSingleton {

    private final ApplicationContext applicationContext;

    @Override
    public void afterSingletonsInstantiated() {
        Map<String, PlatformTransactionManager> tms =
                applicationContext.getBeansOfType(PlatformTransactionManager.class);

        Set<String> jdbcManagers = new LinkedHashSet<>();

        for (var e : tms.entrySet()) {
            String name = e.getKey();
            PlatformTransactionManager bean = e.getValue();

            Class<?> targetClass = AopUtils.getTargetClass(bean);
            if (targetClass == null) targetClass = bean.getClass();

            // 1) DataSourceTransactionManager (e subclasses)
            boolean isDsTm = DataSourceTransactionManager.class.isAssignableFrom(targetClass);

            // 2) JdbcTransactionManager (Spring), sem dependência direta (por reflexão)
            boolean isJdbcTm = isClassOrSuperclassNamed(targetClass, "org.springframework.jdbc.support.JdbcTransactionManager");

            if (isDsTm || isJdbcTm) {
                jdbcManagers.add(name + " -> " + targetClass.getName());
            }
        }

        if (!jdbcManagers.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("\n\n🚫 Proibido: TransactionManager JDBC encontrado no contexto Spring.\n");
            sb.append("Isso pode causar: 'Pre-bound JDBC Connection found!...'\n\n");
            sb.append("TransactionManagers JDBC detectados:\n");
            for (String s : jdbcManagers) sb.append(" - ").append(s).append("\n");
            sb.append("\nCorreção típica:\n");
            sb.append(" - Garanta que o bean padrão 'transactionManager' aponte para JpaTransactionManager (PUBLIC).\n");
            sb.append(" - Remova qualquer @Bean DataSourceTransactionManager / JdbcTransactionManager.\n");
            sb.append(" - Verifique auto-config do Spring Boot criando TM JDBC por acidente.\n\n");
            throw new IllegalStateException(sb.toString());
        }
    }

    private static boolean isClassOrSuperclassNamed(Class<?> type, String fqcn) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            if (fqcn.equals(current.getName())) return true;
            current = current.getSuperclass();
        }
        return false;
    }
}