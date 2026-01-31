package brito.com.multitenancy001.tenant.auth.api.dto;

import java.util.List;

public record TenantLoginAmbiguousResponse(
        String code,
        String message,
        String challengeId,
        List<TenantSelectionOption> candidates
) {}
