package brito.com.multitenancy001.shared.domain.audit.jpa;

import brito.com.multitenancy001.shared.domain.audit.Auditable;
import brito.com.multitenancy001.shared.domain.audit.SoftDeletable;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Verificador FAIL-FAST (ENFORCED) para auditoria.
 *
 * O que valida no startup:
 *
 * 1) Wiring obrigatório:
 *    - AuditActorProvider registrado
 *    - AuditClockProvider registrado
 *
 * 2) Modelo obrigatório (SaaS-grade):
 *    - toda @Entity do TENANT => implements {@link Auditable}
 *    - toda @Entity com assinatura de soft delete => implements {@link SoftDeletable}
 *
 * Resultado:
 * - Se houver violações, quebra o startup com "lista de violações" (estilo compliance verifier).
 *
 * Heurísticas (ajuste se necessário):
 * - "Tenant entity" = package contém ".tenant." e NÃO contém ".controlplane." e NÃO contém ".publicschema."
 * - "Soft delete signature" = campo deleted (boolean/Boolean) OU método isDeleted() OU métodos softDelete()/restore()
 */
@Component
@RequiredArgsConstructor
public class AuditWiringVerifier {

    private final EntityManager entityManager;

    @PostConstruct
    public void verifyAuditWiring() {
        /* Verifica wiring e enforcement de modelo (fail-fast). */

        // -------------------------------------------------
        // 1) Wiring obrigatório (como hoje)
        // -------------------------------------------------
        AuditActorProviders.requireRegistered();
        AuditClockProviders.requireRegistered();

        // -------------------------------------------------
        // 2) Enforced model checks
        // -------------------------------------------------
        List<String> violations = new ArrayList<>();

        for (EntityType<?> et : entityManager.getMetamodel().getEntities()) {
            Class<?> javaType = et.getJavaType();
            if (javaType == null) continue;

            if (!isTenantEntity(javaType)) continue;

            // tenant @Entity => Auditable
            if (!Auditable.class.isAssignableFrom(javaType)) {
                violations.add("[TENANT_ENTITY_NOT_AUDITABLE] " + javaType.getName()
                        + " => deve implementar Auditable");
            }

            // soft delete signature => SoftDeletable
            if (looksLikeSoftDelete(javaType) && !SoftDeletable.class.isAssignableFrom(javaType)) {
                violations.add("[SOFT_DELETE_WITHOUT_SOFTDELETABLE] " + javaType.getName()
                        + " => parece ter soft delete (deleted/isDeleted/softDelete/restore), mas não implementa SoftDeletable");
            }
        }

        if (!violations.isEmpty()) {
            violations.sort(Comparator.naturalOrder());
            throw new IllegalStateException(buildFailFastMessage(violations));
        }
    }

    private static boolean isTenantEntity(Class<?> type) {
        /* Decide por package se a entidade pertence ao bounded context TENANT. */
        Package p = type.getPackage();
        String name = (p == null) ? "" : p.getName();

        if (!name.contains(".tenant.")) return false;
        if (name.contains(".controlplane.")) return false;
        if (name.contains(".publicschema.")) return false;

        return true;
    }

    private static boolean looksLikeSoftDelete(Class<?> type) {
        /* Heurística para detectar assinatura típica de soft delete no domínio. */

        // Campo "deleted"
        Field deleted = findField(type, "deleted");
        if (deleted != null && (deleted.getType() == boolean.class || deleted.getType() == Boolean.class)) {
            return true;
        }

        // Método "isDeleted()"
        Method isDeleted = findNoArgMethod(type, "isDeleted");
        if (isDeleted != null && (isDeleted.getReturnType() == boolean.class || isDeleted.getReturnType() == Boolean.class)) {
            return true;
        }

        // Métodos comuns de domínio
        if (findNoArgMethod(type, "softDelete") != null) return true;
        if (findNoArgMethod(type, "restore") != null) return true;

        return false;
    }

    private static Field findField(Class<?> type, String fieldName) {
        /* Procura campo por nome subindo a hierarquia. */
        Class<?> c = type;
        while (c != null && c != Object.class) {
            try {
                return c.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    private static Method findNoArgMethod(Class<?> type, String methodName) {
        /* Procura método sem args por nome subindo a hierarquia. */
        Class<?> c = type;
        while (c != null && c != Object.class) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(methodName) && m.getParameterCount() == 0) {
                    return m;
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }

    private static String buildFailFastMessage(List<String> violations) {
        /* Monta mensagem estilo compliance verifier para troubleshooting rápido. */
        StringBuilder sb = new StringBuilder();
        sb.append("AUDIT_WIRING_VERIFIER_FAILED (ENFORCED)\n\n");
        sb.append("Foram encontradas violações de auditoria:\n\n");
        for (String v : violations) {
            sb.append(" - ").append(v).append("\n");
        }
        sb.append("\nRegras enforced:\n");
        sb.append(" - tenant @Entity => implements Auditable\n");
        sb.append(" - soft delete signature => implements SoftDeletable\n");
        return sb.toString();
    }
}