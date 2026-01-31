package brito.com.multitenancy001.tenant.api.dto.users;

import brito.com.multitenancy001.shared.security.SystemRoleName;

public record TenantUserDetailsResponse(
        Long id,
        Long accountId,
        String name,
        String email,
        SystemRoleName  role,
        String phone,
        String avatarUrl,
        String timezone,
        String locale,
        boolean suspendedByAccount,
        boolean suspendedByAdmin,
        boolean deleted,
        boolean enabled
) {}
