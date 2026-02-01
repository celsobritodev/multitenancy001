package brito.com.multitenancy001.tenant.auth.app.dto;

import brito.com.multitenancy001.shared.api.dto.auth.JwtResponse;

public sealed interface TenantLoginResult
        permits TenantLoginResult.LoginSuccess, TenantLoginResult.TenantSelectionRequired {

    record LoginSuccess(JwtResponse jwt) implements TenantLoginResult { }

    record TenantSelectionRequired(
            String challengeId,
            java.util.List<TenantSelectionOptionData> details
    ) implements TenantLoginResult { }
}
