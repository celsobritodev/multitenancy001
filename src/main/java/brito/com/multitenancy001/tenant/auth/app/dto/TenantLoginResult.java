package brito.com.multitenancy001.tenant.auth.app.dto;

import brito.com.multitenancy001.shared.auth.app.dto.JwtResult;

public sealed interface TenantLoginResult
        permits TenantLoginResult.LoginSuccess, TenantLoginResult.AccountSelectionRequired {

    record LoginSuccess(JwtResult jwt) implements TenantLoginResult { }

    /**
     * Retornado quando o email existe em mais de uma conta (account/tenant).
     * A seleção é de "Account", mesmo que a autenticação final seja no contexto Tenant.
     */
    record AccountSelectionRequired(
            String challengeId,
            java.util.List<AccountSelectionOptionData> candidates
    ) implements TenantLoginResult { }
}

