package brito.com.multitenancy001.tenant.api.dto.users;

public record TenantUserDetailsResponse(
        Long id,
        Long accountId,
        String name,
        String email,
        String role,
        String phone,
        String avatarUrl,
        String timezone,
        String locale,
        boolean suspendedByAccount,
        boolean suspendedByAdmin,
        boolean deleted,
        boolean enabled
) {}
