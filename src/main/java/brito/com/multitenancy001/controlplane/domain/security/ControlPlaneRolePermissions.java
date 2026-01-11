package brito.com.multitenancy001.controlplane.domain.security;

import java.util.EnumSet;
import java.util.Set;

import brito.com.multitenancy001.controlplane.domain.user.ControlPlaneRole;

public final class ControlPlaneRolePermissions {

    private ControlPlaneRolePermissions() {}

    public static Set<ControlPlanePermission> permissionsFor(ControlPlaneRole role) {
        if (role == null) return Set.of();

        return switch (role) {
            case SUPER_ADMIN -> EnumSet.allOf(ControlPlanePermission.class);

            case BILLING_ADMIN_CP -> EnumSet.of(
                ControlPlanePermission.CP_TENANT_READ,
                ControlPlanePermission.CP_TENANT_SUSPEND,
                ControlPlanePermission.CP_TENANT_ACTIVATE,
                ControlPlanePermission.CP_BILLING_READ,
                ControlPlanePermission.CP_BILLING_WRITE
            );

            case SUPPORT -> EnumSet.of(
                ControlPlanePermission.CP_TENANT_READ,
                ControlPlanePermission.CP_TENANT_SUSPEND,
                ControlPlanePermission.CP_TENANT_ACTIVATE,
                ControlPlanePermission.CP_USER_READ,
                ControlPlanePermission.CP_USER_WRITE
            );

            case STAFF -> EnumSet.of(
                ControlPlanePermission.CP_TENANT_READ,
                ControlPlanePermission.CP_USER_READ
            );
        };
    }
}
