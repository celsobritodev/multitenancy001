package brito.com.multitenancy001.controlplane.security;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Set;

/**
 * Centraliza a matriz Role -> Permissions do Control Plane.
 *
 * Regras:
 * - sempre devolve Set imutável
 * - toda role deve estar mapeada explicitamente (evita "role nova = permissão vazia" por acidente)
 */
public final class ControlPlaneRolePermissions {

    private ControlPlaneRolePermissions() {}

    private static final EnumMap<ControlPlaneRole, Set<ControlPlanePermission>> MAP =
            new EnumMap<>(ControlPlaneRole.class);

    static {
        // OWNER = tudo
        MAP.put(ControlPlaneRole.CONTROLPLANE_OWNER,
                unmodifiable(EnumSet.allOf(ControlPlanePermission.class)));

        // ADMIN = tudo (no CP, admin normalmente é "quase owner")
        MAP.put(ControlPlaneRole.CONTROLPLANE_ADMIN,
                unmodifiable(EnumSet.allOf(ControlPlanePermission.class)));

        // BILLING_MANAGER = billing + leitura básica do tenant
        MAP.put(ControlPlaneRole.CONTROLPLANE_BILLING_MANAGER, unmodifiable(EnumSet.of(
                ControlPlanePermission.CP_TENANT_READ,
                ControlPlanePermission.CP_BILLING_READ,
                ControlPlanePermission.CP_BILLING_WRITE
        )));

        // SUPPORT = operações de suporte (inclui reset de senha)
        MAP.put(ControlPlaneRole.CONTROLPLANE_SUPPORT, unmodifiable(EnumSet.of(
                ControlPlanePermission.CP_TENANT_READ,
                ControlPlanePermission.CP_TENANT_SUSPEND,
                ControlPlanePermission.CP_TENANT_RESUME,
                ControlPlanePermission.CP_TENANT_DELETE,
                ControlPlanePermission.CP_USER_READ,
                ControlPlanePermission.CP_USER_WRITE,
                ControlPlanePermission.CP_USER_DELETE,
                ControlPlanePermission.CP_USER_PASSWORD_RESET
        )));

        // OPERATOR = leitura operacional
        MAP.put(ControlPlaneRole.CONTROLPLANE_OPERATOR, unmodifiable(EnumSet.of(
                ControlPlanePermission.CP_TENANT_READ,
                ControlPlanePermission.CP_USER_READ
        )));

        // VIEWER = somente leitura
        MAP.put(ControlPlaneRole.CONTROLPLANE_VIEWER, unmodifiable(EnumSet.of(
                ControlPlanePermission.CP_TENANT_READ,
                ControlPlanePermission.CP_BILLING_READ,
                ControlPlanePermission.CP_USER_READ
        )));
    }

    public static Set<ControlPlanePermission> permissionsFor(ControlPlaneRole role) {
        if (role == null) return Set.of();
        return MAP.getOrDefault(role, Set.of());
    }

    private static Set<ControlPlanePermission> unmodifiable(EnumSet<ControlPlanePermission> set) {
        return Collections.unmodifiableSet(set);
    }
}
