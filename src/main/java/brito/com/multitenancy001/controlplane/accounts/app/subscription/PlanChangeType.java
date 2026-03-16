package brito.com.multitenancy001.controlplane.accounts.app.subscription;

/**
 * Tipo semântico da mudança de plano.
 */
public enum PlanChangeType {
    UPGRADE,
    DOWNGRADE,
    NO_CHANGE
}