package brito.com.multitenancy001.shared.domain.audit.jpa;

import java.time.Instant;

/**
 * Holder estático para permitir que EntityListeners (instanciados pelo JPA)
 * acessem o tempo via AppClock (bean Spring) sem injeção direta.
 *
 * Regra: em PRODUÇÃO o ideal é FAIL-FAST se o provider não estiver registrado,
 * porque senão você perde determinismo sem perceber.
 *
 * Se você quiser permitir fallback apenas em DEV/TEST, rode a JVM com:
 *   -Daudit.clock.systemFallback=true
 */
public final class AuditClockProviders {

    private static volatile AuditClockProvider provider;

    /**
     * Default: FAIL-FAST.
     * DEV/TEST: pode habilitar fallback com -Daudit.clock.systemFallback=true
     */
    private static final boolean SYSTEM_FALLBACK_ENABLED =
            Boolean.getBoolean("audit.clock.systemFallback");

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
                "Sem isso, a auditoria pode ficar não-determinística. " +
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
        return p.now();
    }

    /**
     * Mantido por compatibilidade.
     * Em geral, prefira nowOrFail() no domínio.
     */
    public static Instant nowOrSystem() {
        AuditClockProvider p = provider;
        if (p == null) {
            if (SYSTEM_FALLBACK_ENABLED) return Instant.now();
            throw new IllegalStateException(
                "AUDIT MISCONFIGURATION: AuditClockProvider ausente e fallback desabilitado. " +
                "Para habilitar fallback apenas em DEV/TEST use: -Daudit.clock.systemFallback=true"
            );
        }
        return p.now();
    }
}
