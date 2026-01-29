package brito.com.multitenancy001.tenant.api.dto.auth;

import java.util.List;

public record TenantLoginAmbiguousResponse(
        String code,
        String message,
        String challengeId,
        List<TenantSelectionOption> candidates
) {}
