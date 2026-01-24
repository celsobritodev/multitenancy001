package brito.com.multitenancy001.infrastructure.security;

/**
 * Constantes de segurança (infra).
 * Mantém strings de domínios e rotas em um só lugar.
 */
public final class SecurityConstants {

    private SecurityConstants() {}

    public static final class AuthDomains {
        private AuthDomains() {}
        public static final String TENANT = "TENANT";
        public static final String CONTROLPLANE = "CONTROLPLANE";
    }

    public static final class ApiPaths {
        private ApiPaths() {}

        public static final String ADMIN_PREFIX = "/api/admin/";
        public static final String CONTROLPLANE_PREFIX = "/api/controlplane/";
        public static final String TENANT_PREFIX = "/api/tenant/";

        // ✅ TENANT "me" fora do prefixo /api/tenant
        public static final String ME = "/api/me";
        public static final String ME_PREFIX = "/api/me/";
    }
}
