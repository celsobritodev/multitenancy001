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
 * - FAIL-FAST: role sem mapeamento explode na inicialização e/ou no uso
 */
public final class ControlPlaneRolePermissions {

    private ControlPlaneRolePermissions() {}

    private static final EnumMap<ControlPlaneRole, Set<ControlPlanePermission>> MAP =
            new EnumMap<>(ControlPlaneRole.class);

    static {
        // OWNER = tudo
        MAP.put(ControlPlaneRole.CONTROLPLANE_OWNER,
                unmodifiable(EnumSet.allOf(ControlPlanePermission.class)));

        // ADMIN = forte, mas sem ações mais destrutivas (produção-friendly)
        // Removemos deletes para evitar "um admin apaga tudo" por erro/ataque.
        EnumSet<ControlPlanePermission> admin = EnumSet.allOf(ControlPlanePermission.class);
        admin.remove(ControlPlanePermission.CP_TENANT_DELETE);
        admin.remove(ControlPlanePermission.CP_USER_DELETE);

        MAP.put(ControlPlaneRole.CONTROLPLANE_ADMIN, unmodifiable(admin));

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

        // FAIL-FAST: garante que todas as roles do enum estão mapeadas
        for (ControlPlaneRole role : ControlPlaneRole.values()) {
            if (!MAP.containsKey(role)) {
                throw new IllegalStateException("Role do ControlPlane sem mapeamento em ControlPlaneRolePermissions: " + role);
            }
        }
    }

    public static Set<ControlPlanePermission> permissionsFor(ControlPlaneRole role) {
        Set<ControlPlanePermission> perms = MAP.get(role);
        if (perms == null) {
            throw new IllegalArgumentException("Role do ControlPlane sem permissões mapeadas: " + role);
        }
        return perms;
    }

    private static Set<ControlPlanePermission> unmodifiable(EnumSet<ControlPlanePermission> set) {
        return Collections.unmodifiableSet(set);
    }
}
