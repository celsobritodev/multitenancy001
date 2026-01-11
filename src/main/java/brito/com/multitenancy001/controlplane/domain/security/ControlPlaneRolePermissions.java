package brito.com.multitenancy001.controlplane.domain.security;

import java.util.EnumSet;
import java.util.Set;

import brito.com.multitenancy001.controlplane.domain.user.ControlPlaneRole;

public final class ControlPlaneRolePermissions {

    private ControlPlaneRolePermissions() {}

    public static Set<ControlPlanePermission> permissionsFor(ControlPlaneRole role) {
        if (role == null) return Set.of();

        return switch (role) {
            case PLATFORM_OWNER -> EnumSet.allOf(ControlPlanePermission.class);

            case PLATFORM_BILLING_MANAGER -> EnumSet.of(
                ControlPlanePermission.CP_TENANT_READ,
                ControlPlanePermission.CP_TENANT_SUSPEND,
                ControlPlanePermission.CP_TENANT_ACTIVATE,
                ControlPlanePermission.CP_BILLING_READ,
                ControlPlanePermission.CP_BILLING_WRITE
            );

            case PLATFORM_SUPPORT -> EnumSet.of(
                ControlPlanePermission.CP_TENANT_READ,
                ControlPlanePermission.CP_TENANT_SUSPEND,
                ControlPlanePermission.CP_TENANT_ACTIVATE,
                ControlPlanePermission.CP_USER_READ,
                ControlPlanePermission.CP_USER_WRITE
            );

            case PLATFORM_OPERATOR -> EnumSet.of(
                ControlPlanePermission.CP_TENANT_READ,
                ControlPlanePermission.CP_USER_READ
            );
        };
    }
}
