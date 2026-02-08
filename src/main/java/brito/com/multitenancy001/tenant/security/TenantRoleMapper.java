package brito.com.multitenancy001.tenant.security;

import brito.com.multitenancy001.shared.security.SystemRoleName;
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
    
    public static SystemRoleName toSystemRoleOrNull(TenantRole tenantRole) {
        if (tenantRole == null) return null;

        return switch (tenantRole) {
            case TENANT_OWNER -> SystemRoleName.TENANT_OWNER;
            case TENANT_ADMIN -> SystemRoleName.TENANT_ADMIN;
            case TENANT_SUPPORT -> SystemRoleName.TENANT_SUPPORT;
            case TENANT_USER -> SystemRoleName.TENANT_USER;
            case TENANT_PRODUCT_MANAGER -> SystemRoleName.TENANT_PRODUCT_MANAGER;
            case TENANT_SALES_MANAGER -> SystemRoleName.TENANT_SALES_MANAGER;
            case TENANT_BILLING_MANAGER -> SystemRoleName.TENANT_BILLING_MANAGER;
            case TENANT_READ_ONLY -> SystemRoleName.TENANT_READ_ONLY;
            case TENANT_OPERATOR -> SystemRoleName.TENANT_OPERATOR;
        };
    }
}

