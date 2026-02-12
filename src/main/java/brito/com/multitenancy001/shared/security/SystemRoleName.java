package brito.com.multitenancy001.shared.security;

public enum SystemRoleName {

    // CONTROL PLANE
    CONTROLPLANE_OWNER,
    CONTROLPLANE_ADMIN,
    CONTROLPLANE_BILLING_MANAGER,
    CONTROLPLANE_SUPPORT,
    CONTROLPLANE_VIEWER,
    CONTROLPLANE_OPERATOR,

    // TENANT
    TENANT_OWNER,
    TENANT_ADMIN,
    TENANT_MANAGER,
    TENANT_SUPPORT,
    TENANT_USER,
    TENANT_PRODUCT_MANAGER,
    TENANT_SALES_MANAGER,
    TENANT_BILLING_MANAGER,
    TENANT_READ_ONLY,
    TENANT_OPERATOR;

    public boolean isControlPlane() { return name().startsWith("CONTROLPLANE_"); }
    public boolean isTenant() { return name().startsWith("TENANT_"); }

    public static SystemRoleName fromString(String value) {
        if (value == null || value.isBlank()) return null;
        return SystemRoleName.valueOf(value.trim().toUpperCase());
    }
}
