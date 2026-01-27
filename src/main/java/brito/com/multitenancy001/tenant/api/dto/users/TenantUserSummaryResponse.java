package brito.com.multitenancy001.tenant.api.dto.users;

public record TenantUserSummaryResponse(
        Long id,
        String email,
        boolean suspendedByAccount,
        boolean suspendedByAdmin,
        boolean enabled
) {}
