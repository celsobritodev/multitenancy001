package brito.com.multitenancy001.controlplane.domain.account;

public enum SubscriptionPlan {
    FREE,
    PRO,
    ENTERPRISE,

    /**
     * Plano interno do sistema (Control Plane).
     * SYSTEM != cliente, não tem trial, não tem billing, não tem entitlements.
     */
    SYSTEM
}
