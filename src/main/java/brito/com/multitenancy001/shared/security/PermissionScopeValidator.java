package brito.com.multitenancy001.shared.security;

import brito.com.multitenancy001.controlplane.security.ControlPlanePermission;
import brito.com.multitenancy001.shared.domain.DomainException;
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
                throw new DomainException("Permission de Control Plane não é permitida no Tenant: " + x);
            }
            if (!x.startsWith("TEN_")) {
                throw new DomainException("Permission inválida (esperado prefixo TEN_): " + x);
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
                throw new DomainException("Permission de Tenant não é permitida no Control Plane: " + x);
            }
            if (!x.startsWith("CP_")) {
                throw new DomainException("Permission inválida (esperado prefixo CP_): " + x);
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
                throw new DomainException("Permission de Control Plane não é permitida no Tenant: " + x);
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
                throw new DomainException("Permission de Tenant não é permitida no Control Plane: " + x);
            }

            if (!x.startsWith("CP_")) x = "CP_" + x;
            out.add(x);
        }
        return out;
    }

    // =========================================================
    // Enum variants
    // =========================================================

    public static LinkedHashSet<TenantPermission> normalizeTenantPermissions(Collection<TenantPermission> perms) {
        LinkedHashSet<TenantPermission> out = new LinkedHashSet<>();
        if (perms == null) return out;

        for (TenantPermission p : perms) {
            if (p == null) continue;

            String name = p.name();

            if (name.startsWith("CP_")) {
                throw new DomainException("Permission de Control Plane não é permitida no Tenant: " + name);
            }
            if (!name.startsWith("TEN_")) {
                throw new DomainException("Permission inválida (esperado prefixo TEN_): " + name);
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
                throw new DomainException("Permission de Tenant não é permitida no Control Plane: " + name);
            }
            if (!name.startsWith("CP_")) {
                throw new DomainException("Permission inválida (esperado CP_): " + name);
            }
            out.add(p);
        }
        return out;
    }
    
    // =========================================================
    // Guards (assert) - úteis para service layer
    // =========================================================

    /**
     * Garante que uma lista de permissões (strings) NÃO contenha TEN_*
     */
    public static void assertNoTenantPermissionLeak(Collection<String> perms) {
        if (perms == null) return;

        for (String p : perms) {
            if (p == null) continue;
            String x = p.trim();
            if (x.isEmpty()) continue;

            if (x.startsWith("TEN_")) {
                throw new DomainException("Permission de Tenant não é permitida no Control Plane: " + x);
            }
        }
    }

    /**
     * Garante que uma lista de permissões (strings) NÃO contenha CP_*
     */
    public static void assertNoControlPlanePermissionLeak(Collection<String> perms) {
        if (perms == null) return;

        for (String p : perms) {
            if (p == null) continue;
            String x = p.trim();
            if (x.isEmpty()) continue;

            if (x.startsWith("CP_")) {
                throw new DomainException("Permission de Control Plane não é permitida no Tenant: " + x);
            }
        }
    }
    
    

    
}
