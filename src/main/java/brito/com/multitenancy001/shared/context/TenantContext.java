package brito.com.multitenancy001.shared.context;

import brito.com.multitenancy001.shared.db.Schemas;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Slf4j
public class TenantContext {

    /**
     * Observação semântica:
     * - PUBLIC = sem tenantSchema (null)
     */
    public static final String PUBLIC_SCHEMA = Schemas.CONTROL_PLANE;

    private static final ThreadLocal<String> TENANT_THREAD_LOCAL = new ThreadLocal<>();

    /**
     * ✅ Retorna o tenantSchema REALMENTE bindado (ou null).
     * public = null
     */
    public static String getOrNull() {
        String tenantSchema = TENANT_THREAD_LOCAL.get();
        return StringUtils.hasText(tenantSchema) ? tenantSchema : null;
    }

    /**
     * ✅ Quando você quer um fallback explícito para public.
     */
    public static String getOrDefaultPublic() {
        String tenantSchema = getOrNull();
        return (tenantSchema != null ? tenantSchema : PUBLIC_SCHEMA);
    }

    public static boolean isPublic() {
        return getOrNull() == null;
    }

    /**
     * ✅ Regra:
     * - NÃO pode mudar tenantSchema dentro de transação.
     * - MAS pode chamar bindTenantSchema() de forma idempotente (sem mudança) dentro de transação.
     *
     * Entrada aqui é "tenantSchema" (já no sentido de execução / contexto).
     * Para "entrada crua" (schemaName), isso deve ser tratado antes (provisioning / validação).
     */
    public static void bindTenantSchema(String tenantSchema) {

        String normalizedTenantSchema = (tenantSchema != null ? tenantSchema.trim() : null);
        String targetTenantSchema = StringUtils.hasText(normalizedTenantSchema) ? normalizedTenantSchema : null; // public = null
        String previousTenantSchema = getOrNull(); // já normalizado (public = null)

        // ✅ Sem mudança: não re-binda e evita log repetido
        if ((previousTenantSchema == null && targetTenantSchema == null)
                || (previousTenantSchema != null && previousTenantSchema.equals(targetTenantSchema))) {

            if (log.isDebugEnabled()) {
                log.debug("🔄 TenantContext.bindTenantSchema sem mudança | thread={} | tenantSchema={}",
                        Thread.currentThread().threadId(),
                        (targetTenantSchema != null ? targetTenantSchema : "PUBLIC(null)"));
            }
            return;
        }

        // 🚫 Mudança REAL -> não permitir dentro de transação
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(
                    "🔥 TenantContext.bindTenantSchema chamado DENTRO de transação! tenantSchema=" + tenantSchema
            );
        }

        // aplica mudança
        if (targetTenantSchema == null) {
            TENANT_THREAD_LOCAL.remove();
            log.info("🔄 Tenant bindado para PUBLIC (null) | anterior={} | thread={}",
                    previousTenantSchema, Thread.currentThread().threadId());
        } else {
            TENANT_THREAD_LOCAL.set(targetTenantSchema);
            log.info("🔄 Tenant bindado | thread={} | {} -> {}",
                    Thread.currentThread().threadId(), previousTenantSchema, targetTenantSchema);
        }
    }

    /**
     * Remove qualquer tenantSchema (equivalente a PUBLIC).
     * Prefira usar publicScope()/scope() com try-with-resources.
     */
    public static void clear() {
        String previousTenantSchema = getOrNull();
        if (previousTenantSchema == null) {
            if (log.isDebugEnabled()) {
                log.debug("🧹 TenantContext.clear sem mudança (já estava PUBLIC) | thread={}",
                        Thread.currentThread().threadId());
            }
            return;
        }

        TENANT_THREAD_LOCAL.remove();
        log.info("🧹 Tenant desbindado | thread={} | anterior={}",
                Thread.currentThread().threadId(), previousTenantSchema);
    }

    // ✅ escopo seguro (restaura o tenantSchema anterior ao sair)
    public static Scope scope(String tenantSchema) {
        String previousTenantSchema = getOrNull();
        bindTenantSchema(tenantSchema);
        return new Scope(previousTenantSchema);
    }

    // ✅ escopo PUBLIC explícito (restaura o tenantSchema anterior ao sair)
    public static Scope publicScope() {
        String previousTenantSchema = getOrNull();
        bindTenantSchema(null); // explícito: public = sem tenantSchema
        return new Scope(previousTenantSchema);
    }

    public static final class Scope implements AutoCloseable {
        private final String previousTenantSchema;
        private boolean closed = false;

        private Scope(String previousTenantSchema) {
            this.previousTenantSchema = previousTenantSchema;
        }

        @Override
        public void close() {
            if (!closed) {
                TenantContext.bindTenantSchema(previousTenantSchema); // restaura exatamente o anterior
                closed = true;
            }
        }
    }
}
