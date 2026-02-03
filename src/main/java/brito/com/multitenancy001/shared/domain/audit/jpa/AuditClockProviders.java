package brito.com.multitenancy001.shared.domain.audit.jpa;

import java.time.Instant;

/**
 * Holder estático para permitir que EntityListeners (instanciados pelo JPA)
 * acessem o tempo via AppClock (bean Spring) sem injeção direta.
 *
 * Motivo:
 * - EntityListeners NÃO são gerenciados pelo Spring por padrão.
 * - Injeção @Autowired em listener costuma falhar silenciosamente.
 */
public final class AuditClockProviders {

    private static volatile AuditClockProvider provider;

    private AuditClockProviders() {}

    static void setProvider(AuditClockProvider provider) {
        AuditClockProviders.provider = provider;
    }

    static void clear() {
        AuditClockProviders.provider = null;
    }

    public static Instant nowOrSystem() {
        AuditClockProvider p = provider;
        if (p == null) return Instant.now(); // fallback "system" (ideal é nunca cair aqui)
        return p.now();
    }
}
