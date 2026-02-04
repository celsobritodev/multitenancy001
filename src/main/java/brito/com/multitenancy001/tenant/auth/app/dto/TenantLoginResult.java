package brito.com.multitenancy001.tenant.auth.app.dto;

import brito.com.multitenancy001.shared.auth.app.dto.JwtResult;

import java.util.List;

public sealed interface TenantLoginResult {

    record LoginSuccess(JwtResult jwt) implements TenantLoginResult {}

    /**
     * Quando o mesmo email existe em mais de uma conta/empresa.
     * challengeId: String (UUID em texto) para simplificar tráfego no app layer e no controller.
     */
    record AccountSelectionRequired(
            String challengeId,
            List<AccountSelectionOptionData> candidates
    ) implements TenantLoginResult {}
}
