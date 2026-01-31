package brito.com.multitenancy001.shared.security;

/**
 * Mapper do contrato TenantRoleName -> SystemRoleName
 * (fica em shared porque TenantRoleName já está em shared).
 */
public final class TenantSystemRoleMapper {

    private TenantSystemRoleMapper() {}

    public static SystemRoleName toSystemRole(TenantRoleName role) {
        if (role == null) return null;
        // nomes são iguais
        return SystemRoleName.valueOf(role.name());
    }
}
