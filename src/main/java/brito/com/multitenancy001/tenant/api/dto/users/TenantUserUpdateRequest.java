package brito.com.multitenancy001.tenant.api.dto.users;

import brito.com.multitenancy001.tenant.security.TenantRole;

/**
 * Request para atualização parcial de usuário no Tenant.
 * Tudo é opcional (PATCH semantics).
 *
 * Login é por EMAIL 
 */
public record TenantUserUpdateRequest(
        String name,
        String email,
        TenantRole role,
        Boolean suspendedByAccount,
        Boolean suspendedByAdmin,
        String locale,
        String timezone
) {}
