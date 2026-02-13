package brito.com.multitenancy001.shared.api.error;

/**
 * Categoria de erro (alto nível) para organização e observabilidade.
 *
 * Importante:
 * - adicionar novos valores é ok
 * - renomear/remover é breaking (pode afetar logs, métricas, etc.)
 */
public enum ApiErrorCategory {

    // Entrada / validação
    VALIDATION,
    REQUEST,

    // Auth / segurança
    AUTH,
    SECURITY,

    // Contextos
    CONTROLPLANE,
    TENANT,

    // Domínios
    ACCOUNTS,
    USERS,
    BILLING,

    // Catálogos do Tenant
    PRODUCTS,
    CATEGORIES,
    SUBCATEGORIES,
    SUPPLIERS,
    SALES,
    REPORTS,
    INVENTORY,

    // Planos/limites
    ENTITLEMENTS,
    QUOTAS,

    // Provisionamento / jobs
    PROVISIONING,

    // Concorrência / conflitos
    CONFLICT,

    // Falhas internas
    INTERNAL
}
