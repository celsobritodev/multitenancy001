package brito.com.multitenancy001.controlplane.security;

import java.util.EnumSet;
import java.util.Set;

public final class ControlPlaneRolePermissions {

    private ControlPlaneRolePermissions() {}

    public static Set<ControlPlanePermission> permissionsFor(ControlPlaneRole role) {
        if (role == null) return Set.of();

        return switch (role) {
            case CONTROLPLANE_OWNER -> EnumSet.of(
                // ✅ mínimo para gestão de usuários de plataforma
                ControlPlanePermission.CP_USER_READ,
                ControlPlanePermission.CP_USER_WRITE,
                
             // ✅ reset de senha de usuários (billing/support/operator/etc)
                ControlPlanePermission.CP_USER_RESET_PASSWORD

                // (opcional) se você permitir deleção de usuários
                // , ControlPlanePermission.CP_USER_DELETE

                // (opcional) se o OWNER também gerencia contas/tenants
                // , ControlPlanePermission.CP_TENANT_READ
                // , ControlPlanePermission.CP_TENANT_SUSPEND
                // , ControlPlanePermission.CP_TENANT_ACTIVATE
                // , ControlPlanePermission.CP_TENANT_DELETE
            );

            case CONTROLPLANE_BILLING_MANAGER -> EnumSet.of(
                ControlPlanePermission.CP_TENANT_READ,
                ControlPlanePermission.CP_TENANT_SUSPEND,
                ControlPlanePermission.CP_TENANT_ACTIVATE,
                ControlPlanePermission.CP_BILLING_READ,
                ControlPlanePermission.CP_BILLING_WRITE,
                ControlPlanePermission.CP_SUPPORT_RESET_PASSWORD
            );

            case CONTROLPLANE_SUPPORT -> EnumSet.of(
                ControlPlanePermission.CP_TENANT_READ,
                ControlPlanePermission.CP_TENANT_SUSPEND,
                ControlPlanePermission.CP_TENANT_ACTIVATE,
                ControlPlanePermission.CP_USER_READ,
                ControlPlanePermission.CP_USER_WRITE,
                ControlPlanePermission.CP_SUPPORT_RESET_PASSWORD
            );

            case CONTROLPLANE_OPERATOR -> EnumSet.of(
                ControlPlanePermission.CP_TENANT_READ,
                ControlPlanePermission.CP_USER_READ,
                ControlPlanePermission.CP_SUPPORT_RESET_PASSWORD
            );
        };
    }
}
