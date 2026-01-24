package brito.com.multitenancy001.tenant.api.dto.me;

public record TenantMeResponse(
        Long id,
        Long accountId,
        String name,
        String username,
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
