package brito.com.multitenancy001.tenant.auth.api.dto;

import java.util.List;

public record TenantSelectionRequiredResponse(
        String code,
        String message,
        String challengeId,
        List<TenantSelectionOption> details
) { }
