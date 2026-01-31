package brito.com.multitenancy001.tenant.users.api.dto;

public record TenantUserSummaryResponse(
        Long id,
        String email,
        boolean suspendedByAccount,
        boolean suspendedByAdmin,
        boolean enabled
) {}
