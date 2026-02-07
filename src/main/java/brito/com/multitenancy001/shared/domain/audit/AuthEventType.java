package brito.com.multitenancy001.shared.domain.audit;

public enum AuthEventType {
    LOGIN_INIT,
    TENANT_SELECTION_REQUIRED,
    LOGIN_CONFIRM,
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    LOGIN_DENIED,
    TOKEN_REFRESH
}
