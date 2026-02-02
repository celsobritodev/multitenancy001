package brito.com.multitenancy001.shared.context;

import brito.com.multitenancy001.shared.db.Schemas;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

@Slf4j
public class TenantContext {

    public static final String PUBLIC_SCHEMA = Schemas.CONTROL_PLANE;

    private static final ThreadLocal<String> TENANT_THREAD_LOCAL = new ThreadLocal<>();

    /**
     * ✅ Retorna o tenantSchema REALMENTE bindado (ou null).
     * public = null
     */
    public static String getOrNull() {
        String t = TENANT_THREAD_LOCAL.get();
        return StringUtils.hasText(t) ? t : null;
    }

    /**
     * ✅ Quando você quer um fallback explícito para public.
     */
    public static String getOrDefaultPublic() {
        String t = getOrNull();
        return (t != null ? t : PUBLIC_SCHEMA);
    }

    public static boolean isPublic() {
        return getOrNull() == null;
    }

    /**
     * ✅ Regra:
     * - NÃO pode mudar tenantSchema dentro de transação.
     * - MAS pode chamar bindTenantSchema() de forma idempotente (sem mudança) dentro de transação.
     */
    public static void bindTenantSchema(String tenantSchema) {

        String normalized = (tenantSchema != null ? tenantSchema.trim() : null);
        String target = StringUtils.hasText(normalized) ? normalized : null; // public = null
        String previous = getOrNull(); // já normalizado (public = null)

        // ✅ Sem mudança: não re-binda e evita log repetido
        // Lembre: isso pode ocorrer dentro de transação (ex.: reentrância / nested public scopes)
        if ((previous == null && target == null) || (previous != null && previous.equals(target))) {
            if (log.isDebugEnabled()) {
                log.debug("🔄 TenantContext.bindTenantSchema sem mudança | thread={} | tenantSchema={}",
                        Thread.currentThread().threadId(),
                        (target != null ? target : "PUBLIC(null)"));
            }
            return;
        }

        // 🚫 A partir daqui, há mudança REAL -> não permitir dentro de transação
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("🔥 TenantContext.bindTenantSchema chamado DENTRO de transação! tenantSchema=" + tenantSchema);
        }

        // aplica mudança
        if (target == null) {
            TENANT_THREAD_LOCAL.remove();
            log.info("🔄 Tenant bindado para PUBLIC (null) | anterior={} | thread={}",
                    previous, Thread.currentThread().threadId());
        } else {
            TENANT_THREAD_LOCAL.set(target);
            log.info("🔄 Tenant bindado | thread={} | {} -> {}",
                    Thread.currentThread().threadId(), previous, target);
        }
    }

    /**
     * @deprecated use {@link #bindTenantSchema(String)}.
     * Mantido por compatibilidade: no código antigo "tenantId" na prática é "tenantSchema".
     */
    @Deprecated
    public static void bind(String tenantId) {
        bindTenantSchema(tenantId);
    }

    /**
     * Remove qualquer tenantSchema (equivalente a PUBLIC).
     * Prefira usar publicScope()/scope() com try-with-resources.
     */
    public static void clear() {
        String previous = getOrNull();
        if (previous == null) {
            if (log.isDebugEnabled()) {
                log.debug("🧹 TenantContext.clear sem mudança (já estava PUBLIC) | thread={}",
                        Thread.currentThread().threadId());
            }
            return;
        }

        TENANT_THREAD_LOCAL.remove();
        log.info("🧹 Tenant desbindado | thread={} | anterior={}",
                Thread.currentThread().threadId(), previous);
    }

    // ✅ escopo seguro (restaura o tenantSchema anterior ao sair)
    public static Scope scope(String tenantSchema) {
        String previous = getOrNull();
        bindTenantSchema(tenantSchema);
        return new Scope(previous);
    }

    // ✅ escopo PUBLIC explícito (restaura o tenantSchema anterior ao sair)
    public static Scope publicScope() {
        String previous = getOrNull();
        bindTenantSchema(null); // explícito: public = sem tenantSchema
        return new Scope(previous);
    }

    public static final class Scope implements AutoCloseable {
        private final String previous;
        private boolean closed = false;

        private Scope(String previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (!closed) {
                TenantContext.bindTenantSchema(previous); // restaura exatamente o anterior
                closed = true;
            }
        }
    }
}
