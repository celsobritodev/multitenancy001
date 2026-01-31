package brito.com.multitenancy001.controlplane.users.api.dto;

import brito.com.multitenancy001.shared.security.SystemRoleName;

public record ControlPlaneMeResponse(
        Long id,
        Long accountId,
        String name,
        String email,
        SystemRoleName  role,
        boolean suspendedByAccount,
        boolean suspendedByAdmin,
        boolean deleted,
        boolean enabled
) {}
