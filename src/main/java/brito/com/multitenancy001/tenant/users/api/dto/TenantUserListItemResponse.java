package brito.com.multitenancy001.tenant.users.api.dto;

import java.time.Instant;
import java.util.List;

import brito.com.multitenancy001.shared.domain.common.EntityOrigin;
import brito.com.multitenancy001.shared.security.SystemRoleName;

public record TenantUserListItemResponse(
        Long id,
        String email,

        // RBAC (somente para TENANT_OWNER)
        SystemRoleName role,
        List<String> permissions,

        // Flags/meta
        boolean mustChangePassword,
        EntityOrigin origin,

        // Audit/meta (somente para TENANT_OWNER)
        Instant lastLoginAt,
        TenantActorRef createdBy,

        // Status
        boolean suspendedByAccount,
        boolean suspendedByAdmin,
        boolean enabled
) {}

