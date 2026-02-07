package brito.com.multitenancy001.shared.domain.audit;

import java.util.Locale;

public enum AuthDomain {
    TENANT("tenant"),
    CONTROLPLANE("controlplane"),
    REFRESH("refresh"),
    PASSWORD_RESET("password_reset");

    private final String dbValue;

    AuthDomain(String dbValue) {
        this.dbValue = dbValue;
    }

    /**
     * Valor persistido no banco/audit (minúsculo, sem mudar DDL).
     */
    public String dbValue() {
        return dbValue;
    }

    /**
     * Valor canônico para JWT/claims (MAIÚSCULO), compatível com o que você já usa hoje:
     * TENANT, CONTROLPLANE, REFRESH, PASSWORD_RESET
     */
    public String tokenValue() {
        return name();
    }

    /**
     * Lê do banco/audit (minúsculo).
     */
    public static AuthDomain fromDbValueOrNull(String raw) {
        if (raw == null) return null;
        String v = raw.trim().toLowerCase(Locale.ROOT);
        for (AuthDomain d : values()) {
            if (d.dbValue.equals(v)) return d;
        }
        return null;
    }

    /**
     * Lê do JWT (MAIÚSCULO) e também aceita legado/minúsculo.
     * - "TENANT" -> TENANT
     * - "tenant" -> TENANT (compat)
     * - "PASSWORD_RESET" -> PASSWORD_RESET
     * - "password_reset" -> PASSWORD_RESET (compat)
     */
    public static AuthDomain fromTokenValueOrNull(String raw) {
        if (raw == null) return null;
        String t = raw.trim();
        if (t.isEmpty()) return null;

        // 1) tenta pelo nome do enum (MAIÚSCULO)
        try {
            return AuthDomain.valueOf(t.toUpperCase(Locale.ROOT));
        } catch (Exception ignore) {}

        // 2) tenta mapear pelo dbValue (minúsculo)
        return fromDbValueOrNull(t);
    }
}
