package brito.com.multitenancy001.shared.domain.audit.jpa;

import brito.com.multitenancy001.shared.domain.audit.AuditActor;

/**
 * Holder estático para permitir que EntityListeners (instanciados pelo JPA)
 * acessem o AuditActorProvider (bean Spring) sem injeção direta.
 *
 * Nota: "SYSTEM" é válido quando não existe usuário autenticado.
 * Mas provider ausente é erro de wiring -> fail-fast quando necessário.
 */
public final class AuditActorProviders {

    private static volatile AuditActorProvider provider;

    private AuditActorProviders() {}

    static void setProvider(AuditActorProvider provider) {
        AuditActorProviders.provider = provider;
    }

    static void clear() {
        AuditActorProviders.provider = null;
    }

    /** Usado por wiring verifier / health-checks internos. */
    public static void requireRegistered() {
        if (provider == null) {
            throw new IllegalStateException(
                "AUDIT MISCONFIGURATION: AuditActorProvider NÃO está registrado no AuditActorProviders. " +
                "A auditoria pode registrar tudo como SYSTEM sem você perceber. " +
                "Verifique o bean AuditActorProvider e o @PostConstruct register()."
            );
        }
    }

    /** Fail-fast se provider não estiver registrado (wiring quebrado). */
    public static AuditActor currentOrFail() {
        AuditActorProvider p = provider;
        if (p == null) {
            throw new IllegalStateException(
                "AUDIT MISCONFIGURATION: AuditActorProvider ausente (AuditActorProviders.provider == null)."
            );
        }
        return p.current(); // aqui sim pode retornar SYSTEM legitimamente (sem auth)
    }

    /**
     * Mantido por compatibilidade: se provider estiver ausente, retorna SYSTEM.
     * Útil só como "último recurso".
     */
    public static AuditActor currentOrSystem() {
        AuditActorProvider p = provider;
        if (p == null) return AuditActor.system();
        return p.current();
    }
}
