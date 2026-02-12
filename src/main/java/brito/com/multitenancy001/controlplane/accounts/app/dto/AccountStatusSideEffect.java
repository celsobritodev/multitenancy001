package brito.com.multitenancy001.controlplane.accounts.app.dto;

/**
 * Efeito colateral aplicado no Tenant como consequência da mudança de status do Account.
 *
 * - NONE: nenhuma ação no tenant
 * - SUSPEND_BY_ACCOUNT: suspendeu todos os usuários do tenant por conta
 * - UNSUSPEND_BY_ACCOUNT: reativou (removeu suspensão) de todos os usuários do tenant por conta
 * - CANCEL_ACCOUNT: cancelou conta (soft-delete + status CANCELLED) e soft-delete de usuários no tenant
 */
public enum AccountStatusSideEffect {
    NONE,
    SUSPEND_BY_ACCOUNT,
    UNSUSPEND_BY_ACCOUNT,
    CANCEL_ACCOUNT
}
