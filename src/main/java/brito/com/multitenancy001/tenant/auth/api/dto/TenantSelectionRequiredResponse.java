package brito.com.multitenancy001.tenant.auth.api.dto;

import java.util.List;
import java.util.UUID;

public record TenantSelectionRequiredResponse(
        String code,                 // "TENANT_SELECTION_REQUIRED"
        String message,              // "Selecione o tenant/empresa"
        UUID challengeId,
        List<TenantSelectionOption> details
) {}
