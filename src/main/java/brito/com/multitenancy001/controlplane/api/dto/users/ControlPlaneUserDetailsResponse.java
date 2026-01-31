package brito.com.multitenancy001.controlplane.api.dto.users;

import java.time.LocalDateTime;

import brito.com.multitenancy001.shared.security.SystemRoleName;

public record ControlPlaneUserDetailsResponse(
        Long id,
        Long accountId,
        String name,
        String email,
        SystemRoleName  role,
        boolean suspendedByAccount,
        boolean suspendedByAdmin,
        boolean deleted,
        boolean enabled,
        LocalDateTime createdAt
) {}
