package brito.com.multitenancy001.controlplane.security;

/**
 * Flags de segurança (produção-friendly).
 *
 * Prioridade:
 * 1) System property: app.security.controlplane.admin-can-delete=true|false
 * 2) Env var: APP_SECURITY_CONTROLPLANE_ADMIN_CAN_DELETE=true|false
 * 3) default: false
 */
public final class ControlPlaneSecurityFlags {

    private ControlPlaneSecurityFlags() {}

    public static boolean adminCanDelete() {
        String sys = System.getProperty("app.security.controlplane.admin-can-delete");
        if (sys != null && !sys.isBlank()) {
            return Boolean.parseBoolean(sys.trim());
        }

        String env = System.getenv("APP_SECURITY_CONTROLPLANE_ADMIN_CAN_DELETE");
        if (env != null && !env.isBlank()) {
            return Boolean.parseBoolean(env.trim());
        }

        return false;
    }
}
