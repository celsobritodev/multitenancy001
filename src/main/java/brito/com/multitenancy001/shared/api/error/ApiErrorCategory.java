package brito.com.multitenancy001.shared.api.error;

/**
 * Categorias semânticas de erro para governança (DDD / Clean Code).
 * Não é "o status HTTP" — é o agrupamento lógico do erro.
 */
public enum ApiErrorCategory {

    VALIDATION,
    ACCOUNT,
    ENTITLEMENTS,
    SECURITY,
    BILLING,
    PROVISIONING,
    DATA_INTEGRITY,
    DOMAIN,
    SYSTEM
}
