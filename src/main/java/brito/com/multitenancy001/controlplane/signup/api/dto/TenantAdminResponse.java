package brito.com.multitenancy001.controlplane.signup.api.dto;

import brito.com.multitenancy001.shared.security.TenantRoleName;

/**
 * DTO do ControlPlane (Signup).
 *
 * Mantém role tipada sem depender do enum do contexto Tenant.
 */
public record TenantAdminResponse(
        Long id,
        String email,
        TenantRoleName role
) {}

