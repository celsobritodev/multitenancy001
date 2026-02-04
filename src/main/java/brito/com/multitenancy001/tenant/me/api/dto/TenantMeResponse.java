package brito.com.multitenancy001.tenant.me.api.dto;

import brito.com.multitenancy001.shared.domain.common.EntityOrigin;
import brito.com.multitenancy001.shared.security.SystemRoleName;

public record TenantMeResponse(
        Long id,
        Long accountId,
        String name,
        String email,
        SystemRoleName role,
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

