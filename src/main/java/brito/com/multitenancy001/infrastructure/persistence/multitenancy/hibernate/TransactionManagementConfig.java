package brito.com.multitenancy001.infrastructure.persistence.multitenancy.hibernate;

import brito.com.multitenancy001.infrastructure.persistence.tx.TenantReadOnlyTx;
import brito.com.multitenancy001.infrastructure.persistence.tx.TenantTx;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.TransactionManagementConfigurer;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Configuration
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@EnableTransactionManagement
@RequiredArgsConstructor
public class TransactionManagementConfig implements TransactionManagementConfigurer, SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(TransactionManagementConfig.class);

    private static final String TENANT_BASE_PACKAGE = "brito.com.multitenancy001.tenant.";

    private final ApplicationContext applicationContext;

    @Qualifier("publicTransactionManager")
    private final PlatformTransactionManager publicTransactionManager;

    /**
     * ✅ Regra 1: qualquer @Transactional "pelado" (sem especificar manager) deve cair no PUBLIC.
     * Isso torna o comportamento estável e previsível.
     */
    @Override
    public PlatformTransactionManager annotationDrivenTransactionManager() {
        Class<?> target = AopUtils.getTargetClass(publicTransactionManager);
        if (target == null) target = publicTransactionManager.getClass();
        log.info("✅ annotationDrivenTransactionManager() = publicTransactionManager | type={}", target.getName());
        return publicTransactionManager;
    }

    /**
     * ✅ Regra 2: no pacote TENANT, é proibido usar @Transactional diretamente.
     * Deve usar @TenantTx ou @TenantReadOnlyTx (meta-annotations).
     *
     * Se encontrar violações, falha o startup com uma lista de pontos.
     */
    @Override
    public void afterSingletonsInstantiated() {
        log.info("🔎 Verificando uso proibido de @Transactional direto em TENANT... basePackage={}", TENANT_BASE_PACKAGE);

        Set<String> violations = new LinkedHashSet<>();

        // 2.1) Verifica beans normais cuja classe alvo está no pacote tenant
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            Object bean;
            try {
                bean = applicationContext.getBean(beanName);
            } catch (Exception ignored) {
                continue;
            }

            Class<?> targetClass = AopUtils.getTargetClass(bean);
            if (targetClass == null) continue;

            if (isTenantType(targetClass)) {
                violations.addAll(findDirectTransactionalUsages(targetClass));
            }
        }

        // 2.2) Verifica interfaces de Spring Data JPA repositories (proxy)
        Map<String, ?> repositories = applicationContext.getBeansOfType(JpaRepository.class);
        for (Object repoBeanObj : repositories.values()) {

            Class<?> targetClass = AopUtils.getTargetClass(repoBeanObj);
            if (targetClass == null) continue;

            for (Class<?> itf : targetClass.getInterfaces()) {
                if (itf != null && isTenantType(itf)) {
                    violations.addAll(findDirectTransactionalUsages(itf));
                }
            }
        }

        if (!violations.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("\n\n");
            sb.append("🚫 Encontrado uso PROIBIDO de @Transactional direto dentro de TENANT.\n");
            sb.append("Regra do projeto: em '").append(TENANT_BASE_PACKAGE).append("..' use APENAS @TenantTx / @TenantReadOnlyTx.\n\n");
            sb.append("Ocorrências:\n");
            for (String v : violations) {
                sb.append(" - ").append(v).append("\n");
            }
            sb.append("\nCorreção: troque @Transactional por @TenantTx ou @TenantReadOnlyTx.\n");
            throw new IllegalStateException(sb.toString());
        }

        log.info("✅ OK: Nenhum @Transactional direto encontrado no pacote TENANT.");
    }

    private boolean isTenantType(Class<?> type) {
        return type.getName().startsWith(TENANT_BASE_PACKAGE);
    }

    private List<String> findDirectTransactionalUsages(Class<?> type) {
        List<String> out = new ArrayList<>();

        boolean hasClassLevelDirectTransactional = type.isAnnotationPresent(Transactional.class);
        boolean hasClassLevelTenantTx = AnnotatedElementUtils.hasAnnotation(type, TenantTx.class)
                || AnnotatedElementUtils.hasAnnotation(type, TenantReadOnlyTx.class);

        if (hasClassLevelDirectTransactional && !hasClassLevelTenantTx) {
            out.add(type.getName() + "  [@Transactional direto no nível de classe]");
        }

        ReflectionUtils.doWithMethods(type, method -> {
            boolean hasTenantTx = AnnotatedElementUtils.hasAnnotation(method, TenantTx.class)
                    || AnnotatedElementUtils.hasAnnotation(method, TenantReadOnlyTx.class)
                    || AnnotatedElementUtils.hasAnnotation(type, TenantTx.class)
                    || AnnotatedElementUtils.hasAnnotation(type, TenantReadOnlyTx.class);

            if (hasTenantTx) return;

            boolean hasAnyTransactional = AnnotatedElementUtils.hasAnnotation(method, Transactional.class)
                    || AnnotatedElementUtils.hasAnnotation(type, Transactional.class);

            if (!hasAnyTransactional) return;

            if (method.isAnnotationPresent(Transactional.class)) {
                out.add(type.getName() + "#" + signature(method));
            }
        }, this::isCandidateMethod);

        return out;
    }

    private boolean isCandidateMethod(Method method) {
        return !method.isBridge() && !method.isSynthetic();
    }

    private String signature(Method m) {
        StringBuilder sb = new StringBuilder();
        sb.append(m.getName()).append("(");
        Class<?>[] p = m.getParameterTypes();
        for (int i = 0; i < p.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(p[i].getSimpleName());
        }
        sb.append(")");
        return sb.toString();
    }
}