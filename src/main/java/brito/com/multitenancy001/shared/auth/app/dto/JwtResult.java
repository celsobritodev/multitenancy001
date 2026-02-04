package brito.com.multitenancy001.shared.auth.app.dto;

import brito.com.multitenancy001.shared.security.SystemRoleName;

public record JwtResult(
        String accessToken,
        String refreshToken,
        String tokenType,
        Long userId,
        String email,
        SystemRoleName role,
        Long accountId,
        String tenantSchema
) {
    public JwtResult {
        if (tokenType == null || tokenType.isBlank()) tokenType = "Bearer";
    }

    public JwtResult(
            String accessToken,
            String refreshToken,
            Long userId,
            String email,
            SystemRoleName role,
            Long accountId,
            String tenantSchema
    ) {
        this(accessToken, refreshToken, "Bearer", userId, email, role, accountId, tenantSchema);
    }
}

