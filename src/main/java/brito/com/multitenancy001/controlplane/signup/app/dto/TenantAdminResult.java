package brito.com.multitenancy001.controlplane.signup.app.dto;

import brito.com.multitenancy001.shared.security.TenantRoleName;

public record TenantAdminResult(
        Long id,
        String email,
        TenantRoleName role
) {}

