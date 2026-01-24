package brito.com.multitenancy001.controlplane.api.dto.signup;

import brito.com.multitenancy001.tenant.security.TenantRole;

public record TenantAdminResponse(
        Long id,
        String email,
        String username,
        TenantRole role
) {}
