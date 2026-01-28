package brito.com.multitenancy001.shared.security;

import brito.com.multitenancy001.shared.domain.DomainException;

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
    // TIPADO (genérico) - NÃO depende de enums concretos
    // =========================================================

    /**
     * Normaliza/valida permissões do Tenant em formato tipado (enum).
     *
     * Regra:
     * - não aceita CP_*
     * - exige prefixo TEN_*
     *
     * Importante:
     * - T é inferido pelo compilador (ex.: TenantPermission).
     * - O shared não precisa conhecer o enum concreto.
     */
    public static <T extends Enum<T> & PermissionCode> LinkedHashSet<T> normalizeTenantPermissions(Collection<T> perms) {
        LinkedHashSet<T> out = new LinkedHashSet<>();
        if (perms == null) return out;

        for (T p : perms) {
            if (p == null) continue;

            String code = p.asAuthority(); // tipado + compat com sua infra de authorities
            if (code == null) continue;

            String x = code.trim();
            if (x.isEmpty()) continue;

            if (x.startsWith("CP_")) {
                throw new DomainException("Permission de Control Plane não é permitida no Tenant: " + x);
            }
            if (!x.startsWith("TEN_")) {
                throw new DomainException("Permission inválida (esperado prefixo TEN_): " + x);
            }

            out.add(p);
        }

        return out;
    }

    /**
     * Normaliza/valida permissões do ControlPlane em formato tipado (enum).
     *
     * Regra:
     * - não aceita TEN_*
     * - exige prefixo CP_*
     */
    public static <T extends Enum<T> & PermissionCode> LinkedHashSet<T> normalizeControlPlanePermissions(Collection<T> perms) {
        LinkedHashSet<T> out = new LinkedHashSet<>();
        if (perms == null) return out;

        for (T p : perms) {
            if (p == null) continue;

            String code = p.asAuthority();
            if (code == null) continue;

            String x = code.trim();
            if (x.isEmpty()) continue;

            if (x.startsWith("TEN_")) {
                throw new DomainException("Permission de Tenant não é permitida no Control Plane: " + x);
            }
            if (!x.startsWith("CP_")) {
                throw new DomainException("Permission inválida (esperado prefixo CP_): " + x);
            }

            out.add(p);
        }

        return out;
    }

    // =========================================================
    // Aliases SEMÂNTICOS (para ficar claro no service layer)
    // =========================================================

    public static <T extends Enum<T> & PermissionCode> LinkedHashSet<T> validateTenantPermissionsStrict(Collection<T> perms) {
        return normalizeTenantPermissions(perms);
    }

    public static <T extends Enum<T> & PermissionCode> LinkedHashSet<T> validateControlPlanePermissionsStrict(Collection<T> perms) {
        return normalizeControlPlanePermissions(perms);
    }

    // =========================================================
    // Guards (assert) - úteis para service layer
    // =========================================================

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
