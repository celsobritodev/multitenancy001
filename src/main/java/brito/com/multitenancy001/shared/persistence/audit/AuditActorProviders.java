package brito.com.multitenancy001.shared.persistence.audit;

import brito.com.multitenancy001.shared.domain.audit.AuditActor;

/**
 * Holder estático para permitir que EntityListeners (instanciados pelo JPA)
 * acessem o AuditActorProvider (bean Spring) sem injeção direta.
 *
 * Motivo:
 * - EntityListeners NÃO são gerenciados pelo Spring por padrão.
 * - Injeção @Autowired em listener costuma falhar silenciosamente.
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

    public static AuditActor currentOrSystem() {
        AuditActorProvider p = provider;
        if (p == null) return AuditActor.system();
        return p.current();
    }
}
