package brito.com.multitenancy001.tenant.users.api.dto;

import brito.com.multitenancy001.shared.domain.common.EntityOrigin;
import brito.com.multitenancy001.tenant.security.TenantRole;

public record TenantUserDetailsResponse(
        Long id,
        Long accountId,
        String name,
        String email,
        TenantRole role,
        String phone,
        String avatarUrl,
        String timezone,
        String locale,
        boolean mustChangePassword,
        EntityOrigin origin,
        boolean suspendedByAccount,
        boolean suspendedByAdmin,
        boolean deleted,
        boolean enabled
) {}
