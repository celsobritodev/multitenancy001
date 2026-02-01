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
     * ✅ Retorna o tenant REALMENTE bindado (ou null).
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
     * - NÃO pode mudar tenant dentro de transação.
     * - MAS pode chamar bind() de forma idempotente (sem mudança) dentro de transação.
     */
    public static void bind(String tenantId) {

        String normalized = (tenantId != null ? tenantId.trim() : null);
        String target = StringUtils.hasText(normalized) ? normalized : null; // public = null
        String previous = getOrNull(); // já normalizado (public = null)

        // ✅ Sem mudança: não re-binda e evita log repetido
        // Remember: isso pode ocorrer dentro de transação (ex.: reentrância / nested public scopes)
        if ((previous == null && target == null) || (previous != null && previous.equals(target))) {
            if (log.isDebugEnabled()) {
                log.debug("🔄 TenantContext.bind sem mudança | thread={} | tenant={}",
                        Thread.currentThread().threadId(),
                        (target != null ? target : "PUBLIC(null)"));
            }
            return;
        }

        // 🚫 A partir daqui, há mudança REAL -> não permitir dentro de transação
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("🔥 TenantContext.bind chamado DENTRO de transação! tenant=" + tenantId);
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
     * Remove qualquer tenant (equivalente a PUBLIC).
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

    // ✅ escopo seguro (restaura o tenant anterior ao sair)
    public static Scope scope(String tenantId) {
        String previous = getOrNull();
        bind(tenantId);
        return new Scope(previous);
    }

    // ✅ escopo PUBLIC explícito (restaura o tenant anterior ao sair)
    public static Scope publicScope() {
        String previous = getOrNull();
        bind(null); // explícito: public = sem tenant
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
                TenantContext.bind(previous); // restaura exatamente o anterior
                closed = true;
            }
        }
    }
}
