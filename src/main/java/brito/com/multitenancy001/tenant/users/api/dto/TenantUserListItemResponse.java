package brito.com.multitenancy001.tenant.users.api.dto;

import brito.com.multitenancy001.shared.domain.common.EntityOrigin;
import brito.com.multitenancy001.tenant.security.TenantRole;

import java.time.Instant;
import java.util.List;

public record TenantUserListItemResponse(
        Long id,
        String email,
        TenantRole role,
        List<String> permissions,
        boolean mustChangePassword,
        EntityOrigin origin,
        Instant lastLoginAt,
        TenantActorRef createdBy,
        boolean suspendedByAccount,
        boolean suspendedByAdmin,
        boolean enabled
) {}
