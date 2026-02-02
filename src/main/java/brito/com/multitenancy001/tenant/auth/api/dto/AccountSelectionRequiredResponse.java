package brito.com.multitenancy001.tenant.auth.api.dto;

import java.util.List;

public record AccountSelectionRequiredResponse(
        String code,
        String message,
        String challengeId,
        List<AccountSelectionOption> candidates
) { }
