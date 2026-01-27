package brito.com.multitenancy001.controlplane.api.dto.users.summary;

import brito.com.multitenancy001.shared.security.TenantRoleName;

/**
 * Resumo de usuário de uma conta (visão ControlPlane).
 *
 * Role é tipada usando TenantRoleName (contrato compartilhado),
 * sem dependência do enum interno do Tenant.
 */
public record AccountTenantUserSummaryResponse(
        Long id,
        Long accountId,
        String name,
        String email,
        TenantRoleName role,
        boolean suspendedByAccount,
        boolean suspendedByAdmin,
        boolean enabled
) {}
