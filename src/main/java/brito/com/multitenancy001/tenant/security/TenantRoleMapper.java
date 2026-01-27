package brito.com.multitenancy001.tenant.security;

import brito.com.multitenancy001.shared.security.TenantRoleName;

/**
 * Mapper local do contexto Tenant.
 * Aqui o Tenant conhece o shared contract, mas o shared não conhece o Tenant.
 */
public final class TenantRoleMapper {

    private TenantRoleMapper() {}

    public static TenantRole toTenantRole(TenantRoleName roleName) {
        if (roleName == null) return null;
        return TenantRole.valueOf(roleName.name());
    }

    public static TenantRoleName toRoleName(TenantRole role) {
        if (role == null) return null;
        return TenantRoleName.valueOf(role.name());
    }
}
