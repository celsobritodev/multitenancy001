package brito.com.multitenancy001.controlplane.accounts.app.subscription;

/**
 * Tipos de violação de elegibilidade de mudança de plano.
 */
public enum PlanEligibilityViolationType {
    USERS_LIMIT_EXCEEDED,
    PRODUCTS_LIMIT_EXCEEDED,
    STORAGE_LIMIT_EXCEEDED,
    BUILTIN_PLAN_NOT_ALLOWED,
    TARGET_PLAN_NOT_ALLOWED,
    SAME_PLAN_NOT_ALLOWED
}