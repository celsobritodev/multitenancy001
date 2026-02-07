package brito.com.multitenancy001.tenant.auth.app.dto;

import brito.com.multitenancy001.shared.auth.app.dto.JwtResult;

import java.util.List;

public sealed interface TenantLoginResult {

    record LoginSuccess(JwtResult jwt) implements TenantLoginResult {}

    /**
     * NOVO (semântico): quando email+senha validam para mais de um tenant
     */
    record TenantSelectionRequired(
            String challengeId,
            List<TenantSelectionOptionData> details
    ) implements TenantLoginResult {}
}
