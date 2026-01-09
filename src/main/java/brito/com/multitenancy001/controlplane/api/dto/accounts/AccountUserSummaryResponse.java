package brito.com.multitenancy001.controlplane.api.dto.accounts;

public record AccountUserSummaryResponse(
        Long id,
        String username,
        String email,
        boolean suspendedByAccount,
        boolean suspendedByAdmin,
        boolean enabled
) {}
