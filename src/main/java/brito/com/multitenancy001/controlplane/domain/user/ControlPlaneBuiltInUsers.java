package brito.com.multitenancy001.controlplane.domain.user;

import brito.com.multitenancy001.shared.domain.EmailNormalizer;

import java.util.Set;

/**
 * Emails reservados do sistema (BUILT_IN).
 *
 * Regra:
 * - comparação deve ser case-insensitive via CITEXT no banco;
 * - aqui normalizamos uma única vez via EmailNormalizer (trim + lower) para manter consistência.
 */
public final class ControlPlaneBuiltInUsers {

    private ControlPlaneBuiltInUsers() {}

    public static final String SUPERADMIN_EMAIL = "superadmin@platform.local";
    public static final String BILLING_EMAIL    = "billing@platform.local";
    public static final String SUPPORT_EMAIL    = "support@platform.local";
    public static final String OPERATOR_EMAIL   = "operator@platform.local";

    public static final Set<String> RESERVED_EMAILS = Set.of(
            SUPERADMIN_EMAIL,
            BILLING_EMAIL,
            SUPPORT_EMAIL,
            OPERATOR_EMAIL
    );

    public static boolean isReservedEmail(String email) {
        String norm = EmailNormalizer.normalizeOrNull(email);
        if (norm == null) return false;
        return RESERVED_EMAILS.contains(norm);
    }
}
