package brito.com.multitenancy001.tenant.users.api.dto;

import brito.com.multitenancy001.tenant.security.TenantPermission;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

/**
 * Request para atualizar permissões adicionais de um usuário no Tenant.
 *
 * - Role permanece sendo a base (TenantRolePermissions).
 * - Este request define permissões extras (whitelist) para somar à base.
 *
 * Obs: Permissões são TIPADAS (TenantPermission).
 */
public record TenantUserPermissionsUpdateRequest(
        @NotNull(message = "permissions é obrigatório")
        Set<TenantPermission> permissions
) {}
