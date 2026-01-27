package brito.com.multitenancy001.controlplane.domain.user;

import java.util.Locale;
import java.util.Set;

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
        if (email == null) return false;
        return RESERVED_EMAILS.contains(email.trim().toLowerCase(Locale.ROOT));
    }
}
