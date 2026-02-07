package brito.com.multitenancy001.infrastructure.security;

import brito.com.multitenancy001.shared.domain.audit.AuthDomain;

import java.util.Objects;

/**
 * Constantes de segurança (infra).
 * Mantém strings de domínios e rotas em um só lugar.
 */
public final class SecurityConstants {

    private SecurityConstants() {}

    public static final class AuthDomains {
        private AuthDomains() {}

        // ✅ Strings canônicas para JWT (sem "strings mágicas" espalhadas)
        public static final String TENANT = AuthDomain.TENANT.tokenValue();
        public static final String CONTROLPLANE = AuthDomain.CONTROLPLANE.tokenValue();
        public static final String REFRESH = AuthDomain.REFRESH.tokenValue();
        public static final String PASSWORD_RESET = AuthDomain.PASSWORD_RESET.tokenValue();

        // ✅ Converte enum -> string JWT
        public static String tokenValue(AuthDomain domain) {
            return domain == null ? null : domain.tokenValue();
        }

        // ✅ Converte string JWT/legado -> enum
        public static AuthDomain parseOrNull(String raw) {
            return AuthDomain.fromTokenValueOrNull(raw);
        }

        public static boolean is(String raw, AuthDomain expected) {
            return Objects.equals(parseOrNull(raw), expected);
        }
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
