package brito.com.multitenancy001.controlplane.accounts.domain;

public enum SubscriptionPlan {
    FREE,
    PRO,
    ENTERPRISE,

    /**
     * Plano interno do sistema (Control Plane).
     * BUILTIN != cliente, não tem trial, não tem billing, não tem entitlements.
     */
    BUILT_IN_PLAN
}
