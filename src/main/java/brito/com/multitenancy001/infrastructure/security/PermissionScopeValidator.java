package brito.com.multitenancy001.infrastructure.security;

import brito.com.multitenancy001.controlplane.security.ControlPlanePermission;
import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.tenant.security.TenantPermission;

import java.util.Collection;
import java.util.LinkedHashSet;

public final class PermissionScopeValidator {

    private PermissionScopeValidator() {}

    // =========================================================
    // STRICT (recomendado): exige prefixo correto SEMPRE
    // =========================================================

    public static LinkedHashSet<String> normalizeTenantStrict(Collection<String> perms) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (perms == null) return out;

        for (String p : perms) {
            if (p == null) continue;
            String x = p.trim();
            if (x.isEmpty()) continue;

            if (x.startsWith("CP_")) {
                throw new ApiException("INVALID_PERMISSION_SCOPE",
                        "Permission de Control Plane não é permitida no Tenant: " + x,
                        400);
            }
            if (!x.startsWith("TEN_")) {
                throw new ApiException("INVALID_PERMISSION_FORMAT",
                        "Permission inválida (esperado prefixo TEN_): " + x,
                        400);
            }
            out.add(x);
        }
        return out;
    }

    public static LinkedHashSet<String> normalizeControlPlaneStrict(Collection<String> perms) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (perms == null) return out;

        for (String p : perms) {
            if (p == null) continue;
            String x = p.trim();
            if (x.isEmpty()) continue;

            if (x.startsWith("TEN_")) {
                throw new ApiException("INVALID_PERMISSION_SCOPE",
                        "Permission de Tenant não é permitida no Control Plane: " + x,
                        400);
            }
            if (!x.startsWith("CP_")) {
                throw new ApiException("INVALID_PERMISSION_FORMAT",
                        "Permission inválida (esperado prefixo CP_): " + x,
                        400);
            }
            out.add(x);
        }
        return out;
    }

    // =========================================================
    // LENIENT (legado): auto-prefixa se faltar prefixo
    // =========================================================

    public static LinkedHashSet<String> normalizeTenantLenient(Collection<String> perms) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (perms == null) return out;

        for (String p : perms) {
            if (p == null) continue;
            String x = p.trim();
            if (x.isEmpty()) continue;

            if (x.startsWith("CP_")) {
                throw new ApiException("INVALID_PERMISSION_SCOPE",
                        "Permission de Control Plane não é permitida no Tenant: " + x,
                        400);
            }

            if (!x.startsWith("TEN_")) x = "TEN_" + x;
            out.add(x);
        }
        return out;
    }

    public static LinkedHashSet<String> normalizeControlPlaneLenient(Collection<String> perms) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (perms == null) return out;

        for (String p : perms) {
            if (p == null) continue;
            String x = p.trim();
            if (x.isEmpty()) continue;

            if (x.startsWith("TEN_")) {
                throw new ApiException("INVALID_PERMISSION_SCOPE",
                        "Permission de Tenant não é permitida no Control Plane: " + x,
                        400);
            }

            if (!x.startsWith("CP_")) x = "CP_" + x;
            out.add(x);
        }
        return out;
    }

    // =========================================================
    // Enum variants (já são estritos por natureza)
    // =========================================================

    public static LinkedHashSet<TenantPermission> normalizeTenantPermissions(Collection<TenantPermission> perms) {
        LinkedHashSet<TenantPermission> out = new LinkedHashSet<>();
        if (perms == null) return out;

        for (TenantPermission p : perms) {
            if (p == null) continue;
            String name = p.name();

            if (name.startsWith("CP_")) {
                throw new ApiException("INVALID_PERMISSION_SCOPE",
                        "Permission de Control Plane não é permitida no Tenant: " + name,
                        400);
            }
            if (!name.startsWith("TEN_")) {
                throw new ApiException("INVALID_PERMISSION_SCOPE",
                        "Permission inválida (esperado TEN_): " + name,
                        400);
            }
            out.add(p);
        }
        return out;
    }

    public static LinkedHashSet<ControlPlanePermission> normalizeControlPlanePermissions(Collection<ControlPlanePermission> perms) {
        LinkedHashSet<ControlPlanePermission> out = new LinkedHashSet<>();
        if (perms == null) return out;

        for (ControlPlanePermission p : perms) {
            if (p == null) continue;
            String name = p.name();

            if (name.startsWith("TEN_")) {
                throw new ApiException("INVALID_PERMISSION_SCOPE",
                        "Permission de Tenant não é permitida no Control Plane: " + name,
                        400);
            }
            if (!name.startsWith("CP_")) {
                throw new ApiException("INVALID_PERMISSION_SCOPE",
                        "Permission inválida (esperado CP_): " + name,
                        400);
            }
            out.add(p);
        }
        return out;
    }
}
