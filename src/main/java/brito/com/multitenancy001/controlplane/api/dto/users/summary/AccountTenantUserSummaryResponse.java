package brito.com.multitenancy001.controlplane.api.dto.users.summary;

public record AccountTenantUserSummaryResponse(
        Long id,
        Long accountId,
        String name,
        String username,
        String email,
        String role,
        boolean suspendedByAccount,
        boolean suspendedByAdmin,
        boolean enabled
) {}
