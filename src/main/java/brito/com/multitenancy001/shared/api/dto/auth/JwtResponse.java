package brito.com.multitenancy001.shared.api.dto.auth;

public record JwtResponse(
    String accessToken,
    String refreshToken,
    /**
     * HTTP Authorization scheme (ex: "Bearer").
     * NÃO confundir com o "authDomain" do JWT (TENANT/CONTROLPLANE/etc).
     */
    String tokenType,
    Long userId,
    String username,
    String email,

    /**
     * ✅ Role "name" (ex: CONTROLPLANE_OWNER, TENANT_ADMIN)
     */
    String role,

    Long accountId,

    /**
     * ✅ Para tenant: schema do tenant. Para controlplane: Schemas.CONTROL_PLANE.
     */
    String tenantSchema
) {
    public JwtResponse {
        if (tokenType == null || tokenType.isEmpty()) {
            tokenType = "Bearer";
        }
    }

    public JwtResponse(
            String accessToken, String refreshToken,
            Long userId, String username, String email,
            String role, Long accountId, String tenantSchema
    ) {
        this(accessToken, refreshToken, "Bearer",
             userId, username, email, role, accountId, tenantSchema);
    }
}
