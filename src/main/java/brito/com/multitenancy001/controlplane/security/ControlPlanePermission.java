package brito.com.multitenancy001.controlplane.security;

import brito.com.multitenancy001.shared.security.PermissionAuthority;

public enum ControlPlanePermission implements PermissionAuthority {

    CP_TENANT_READ,
    CP_TENANT_SUSPEND,
    CP_TENANT_ACTIVATE,
    CP_TENANT_DELETE,

    CP_BILLING_READ,
    CP_BILLING_WRITE,

    CP_SUPPORT_RESET_PASSWORD,

    CP_USER_READ,
    CP_USER_WRITE,
    CP_USER_DELETE;

    @Override
    public String asAuthority() {
        return name();
    }
}
