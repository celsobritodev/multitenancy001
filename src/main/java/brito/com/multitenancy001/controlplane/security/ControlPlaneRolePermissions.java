package brito.com.multitenancy001.controlplane.security;

import java.util.EnumSet;
import java.util.Set;

public final class ControlPlaneRolePermissions {

    private ControlPlaneRolePermissions() {}

    public static Set<ControlPlanePermission> permissionsFor(ControlPlaneRole role) {
        if (role == null) return Set.of();

        return switch (role) {

            // 🔐 Super Admin = todas as permissões da plataforma
            case CONTROLPLANE_OWNER -> EnumSet.allOf(ControlPlanePermission.class);

            case CONTROLPLANE_BILLING_MANAGER -> EnumSet.of(
                    ControlPlanePermission.CP_TENANT_READ,
                    ControlPlanePermission.CP_TENANT_SUSPEND,
                    ControlPlanePermission.CP_TENANT_ACTIVATE,
                    ControlPlanePermission.CP_BILLING_READ,
                    ControlPlanePermission.CP_BILLING_WRITE,
                    ControlPlanePermission.CP_SUPPORT_PASSWORD_RESET
            );

            case CONTROLPLANE_SUPPORT -> EnumSet.of(
                    ControlPlanePermission.CP_TENANT_READ,
                    ControlPlanePermission.CP_TENANT_SUSPEND,
                    ControlPlanePermission.CP_TENANT_ACTIVATE,
                    ControlPlanePermission.CP_USER_READ,
                    ControlPlanePermission.CP_USER_WRITE,
                    ControlPlanePermission.CP_SUPPORT_PASSWORD_RESET
            );

            case CONTROLPLANE_OPERATOR -> EnumSet.of(
                    ControlPlanePermission.CP_TENANT_READ,
                    ControlPlanePermission.CP_USER_READ,
                    ControlPlanePermission.CP_SUPPORT_PASSWORD_RESET
            );
        };
    }
}
