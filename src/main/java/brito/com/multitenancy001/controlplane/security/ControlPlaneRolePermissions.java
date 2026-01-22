package brito.com.multitenancy001.controlplane.security;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;

public final class ControlPlaneRolePermissions {

    private ControlPlaneRolePermissions() {}

    private static final EnumMap<ControlPlaneRole, Set<ControlPlanePermission>> MAP = new EnumMap<>(ControlPlaneRole.class);

    static {
        MAP.put(ControlPlaneRole.CONTROLPLANE_OWNER, EnumSet.allOf(ControlPlanePermission.class));

        MAP.put(ControlPlaneRole.CONTROLPLANE_BILLING_MANAGER, EnumSet.of(
                ControlPlanePermission.CP_TENANT_READ,
                ControlPlanePermission.CP_BILLING_READ,
                ControlPlanePermission.CP_BILLING_WRITE
        ));


     // em ControlPlaneRolePermissions static { ... }
        MAP.put(ControlPlaneRole.CONTROLPLANE_SUPPORT, EnumSet.of(
            ControlPlanePermission.CP_TENANT_READ,
            ControlPlanePermission.CP_TENANT_SUSPEND,
            ControlPlanePermission.CP_TENANT_RESUME,
            ControlPlanePermission.CP_TENANT_DELETE,
            ControlPlanePermission.CP_USER_READ,
            ControlPlanePermission.CP_USER_WRITE,
            ControlPlanePermission.CP_USER_DELETE,          // <-- add
            ControlPlanePermission.CP_USER_PASSWORD_RESET
        ));


        MAP.put(ControlPlaneRole.CONTROLPLANE_OPERATOR, EnumSet.of(
                ControlPlanePermission.CP_TENANT_READ,
                ControlPlanePermission.CP_USER_READ
        ));
    }

    public static Set<ControlPlanePermission> permissionsFor(ControlPlaneRole role) {
        if (role == null) return Set.of();
        return Collections.unmodifiableSet(MAP.getOrDefault(role, Set.of()));
    }
}
