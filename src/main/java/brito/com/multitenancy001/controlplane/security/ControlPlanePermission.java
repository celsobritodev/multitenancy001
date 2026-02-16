package brito.com.multitenancy001.controlplane.security;

import brito.com.multitenancy001.shared.security.PermissionCode;

public enum ControlPlanePermission implements PermissionCode {

    // =========================
    // ME (self-service)
    // =========================
    CP_ME_READ,
    CP_ME_PASSWORD_CHANGE,

    // =========================
    // TENANT / ACCOUNTS
    // =========================
    CP_TENANT_READ,
    CP_TENANT_CREATE,   // ✅ novo
    CP_TENANT_SUSPEND,
    CP_TENANT_RESUME,
    CP_TENANT_DELETE,

    // =========================
    // BILLING
    // =========================
    CP_BILLING_READ,
    CP_BILLING_WRITE,

    // =========================
    // USERS (Control Plane)
    // =========================
    CP_USER_READ,
    CP_USER_WRITE,
    CP_USER_DELETE,
    CP_USER_PASSWORD_RESET;

    @Override
    public String asAuthority() {
        return name();
    }
}
