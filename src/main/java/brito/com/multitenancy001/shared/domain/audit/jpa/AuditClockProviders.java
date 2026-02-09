package brito.com.multitenancy001.shared.domain.audit.jpa;

import java.time.Instant;

/**
 * Holder estático para permitir que EntityListeners (instanciados pelo JPA)
 * acessem o tempo via AppClock (bean Spring) sem injeção direta.
 *
 * Regra: FAIL-FAST sempre que o provider não estiver registrado.
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

    /** Usado por wiring verifier / health-checks internos. */
    public static void requireRegistered() {
        if (provider == null) {
            throw new IllegalStateException(
                "AUDIT MISCONFIGURATION: AuditClockProvider NÃO está registrado no AuditClockProviders. " +
                "Verifique o bean AuditClockProvider e o @PostConstruct register()."
            );
        }
    }

    /** Regra padrão: tempo SEMPRE vem do AppClock (fail-fast se wiring quebrou). */
    public static Instant nowOrFail() {
        AuditClockProvider p = provider;
        if (p == null) {
            throw new IllegalStateException(
                "AUDIT MISCONFIGURATION: AuditClockProvider ausente (AuditClockProviders.provider == null). " +
                "Isso indica que o @PostConstruct do AuditClockProvider não executou ou o bean não foi criado."
            );
        }
        return p.appNow();
    }
}
