package brito.com.multitenancy001.shared.domain.audit;

/**
 * Tipos de eventos de autenticação registrados em audit.
 *
 * IMPORTANTE:
 * - Mantém compatibilidade com os tipos já existentes
 * - Adiciona LOGOUT para logout forte (revogação server-side)
 */
public enum AuthEventType {
    LOGIN_INIT,
    TENANT_SELECTION_REQUIRED,
    LOGIN_CONFIRM,
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    LOGIN_DENIED,
    TOKEN_REFRESH,
    LOGOUT
}
