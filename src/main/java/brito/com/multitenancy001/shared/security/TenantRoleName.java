package brito.com.multitenancy001.shared.security;

/**
 * Role do contexto Tenant exposta como "contrato" compartilhado.
 *
 * Motivo: permitir tipagem no ControlPlane (DTOs, contratos, integrações)
 * sem depender de tenant.security.TenantRole (bounded context Tenant).
 *
 * Regra: este enum não deve conter comportamento de segurança, apenas nomes.
 */
public enum TenantRoleName {

	TENANT_OWNER,
    TENANT_ADMIN,
    TENANT_PRODUCT_MANAGER,
    TENANT_SALES_MANAGER,
    TENANT_BILLING_MANAGER,
    TENANT_READ_ONLY,
    TENANT_OPERATOR;

    public static TenantRoleName fromString(String value) {
        if (value == null || value.isBlank()) return null;
        return TenantRoleName.valueOf(value.trim().toUpperCase());
    }
}
