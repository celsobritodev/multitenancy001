package brito.com.multitenancy001.shared.api.dto.auth;

import brito.com.multitenancy001.shared.security.SystemRoleName;

public record JwtResponse(
        String accessToken,
        String refreshToken,

        /**
         * HTTP Authorization scheme (ex: "Bearer").
         * NÃO confundir com o "authDomain" do JWT (TENANT/CONTROLPLANE/etc).
         */
        String tokenType,

        Long userId,

        /**
         * ✅ Identidade canônica de login (única).
         */
        String email,

        /**
         * ✅ Role "name" (ex: CONTROLPLANE_OWNER, TENANT_ADMIN)
         */
        SystemRoleName  role,

        Long accountId,

        /**
         * ✅ Para tenant: schema do tenant. Para controlplane: Schemas.CONTROL_PLANE.
         */
        String tenantSchema
) {
    public JwtResponse {
        if (tokenType == null || tokenType.isBlank()) tokenType = "Bearer";
    }

    /** ✅ Construtor curto (sem tokenType). */
    public JwtResponse(
            String accessToken,
            String refreshToken,
            Long userId,
            String email,
            SystemRoleName  role,
            Long accountId,
            String tenantSchema
    ) {
        this(accessToken, refreshToken, "Bearer", userId, email, role, accountId, tenantSchema);
    }

    public static JwtResponse forEmailLogin(
            String accessToken,
            String refreshToken,
            Long userId,
            String email,
            SystemRoleName  role,
            Long accountId,
            String tenantSchema
    ) {
        return new JwtResponse(accessToken, refreshToken, "Bearer", userId, email, role, accountId, tenantSchema);
    }
}
