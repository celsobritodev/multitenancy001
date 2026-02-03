package brito.com.multitenancy001.shared.security;

import brito.com.multitenancy001.shared.domain.DomainException;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Validador de escopo de permissões (shared).
 *
 * Objetivo:
 * - Evitar "vazamento" de permissions entre bounded contexts (TEN_ vs CP_)
 * - FAIL-FAST: nunca aceitar permission sem prefixo
 * - DDD/layered: shared não conhece enums concretos (somente contratos via PermissionCode)
 *
 * Regras:
 * - Tenant: somente "TEN_*" é aceito (nunca CP_ e nunca sem prefixo)
 * - ControlPlane: somente "CP_*" é aceito (nunca TEN_ e nunca sem prefixo)
 */
public final class PermissionScopeValidator {

    private PermissionScopeValidator() {}

    // =========================================================
    // STRICT (String): exige prefixo correto SEMPRE
    // =========================================================

    public static LinkedHashSet<String> normalizeTenantStrict(Collection<String> perms) {
        return normalizeStringStrict(perms, "TEN_", "CP_", "Tenant", "Control Plane");
    }

    public static LinkedHashSet<String> normalizeControlPlaneStrict(Collection<String> perms) {
        return normalizeStringStrict(perms, "CP_", "TEN_", "Control Plane", "Tenant");
    }

    /** Aliases (continua STRICT). */
    public static LinkedHashSet<String> normalizeTenant(Collection<String> perms) {
        return normalizeTenantStrict(perms);
    }

    public static LinkedHashSet<String> normalizeControlPlane(Collection<String> perms) {
        return normalizeControlPlaneStrict(perms);
    }

    private static LinkedHashSet<String> normalizeStringStrict(
            Collection<String> perms,
            String expectedPrefix,
            String forbiddenPrefix,
            String expectedContextLabel,
            String forbiddenContextLabel
    ) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        if (perms == null) return out;

        for (String p : perms) {
            if (p == null) continue;

            String x = p.trim();
            if (x.isEmpty()) continue;

            if (x.startsWith(forbiddenPrefix)) {
                throw new DomainException("Permission de " + forbiddenContextLabel
                        + " não é permitida no " + expectedContextLabel + ": " + x);
            }

            // ✅ NUNCA aceita permissão sem prefixo
            if (!x.startsWith(expectedPrefix)) {
                throw new DomainException("Permission inválida (esperado prefixo " + expectedPrefix
                        + ") no " + expectedContextLabel + ": " + x);
            }

            out.add(x);
        }

        return out;
    }

    // =========================================================
    // STRICT (Tipado): enums implementando PermissionCode
    // =========================================================

    public static <T extends Enum<T> & PermissionCode> LinkedHashSet<T> normalizeTenantPermissions(Collection<T> perms) {
        return normalizeTypedStrict(perms, "TEN_", "CP_", "Tenant", "Control Plane");
    }

    public static <T extends Enum<T> & PermissionCode> LinkedHashSet<T> normalizeControlPlanePermissions(Collection<T> perms) {
        return normalizeTypedStrict(perms, "CP_", "TEN_", "Control Plane", "Tenant");
    }

    private static <T extends Enum<T> & PermissionCode> LinkedHashSet<T> normalizeTypedStrict(
            Collection<T> perms,
            String expectedPrefix,
            String forbiddenPrefix,
            String expectedContextLabel,
            String forbiddenContextLabel
    ) {
        LinkedHashSet<T> out = new LinkedHashSet<>();
        if (perms == null) return out;

        for (T p : perms) {
            if (p == null) continue;

            String code = p.asAuthority(); // contrato shared
            if (code == null) continue;

            String x = code.trim();
            if (x.isEmpty()) continue;

            if (x.startsWith(forbiddenPrefix)) {
                throw new DomainException("Permission de " + forbiddenContextLabel
                        + " não é permitida no " + expectedContextLabel + ": " + x);
            }

            // ✅ NUNCA aceita permissão sem prefixo
            if (!x.startsWith(expectedPrefix)) {
                throw new DomainException("Permission inválida (esperado prefixo " + expectedPrefix
                        + ") no " + expectedContextLabel + ": " + x);
            }

            out.add(p);
        }

        return out;
    }

    // =========================================================
    // COMPAT (métodos antigos) - agora STRICT (sem auto-prefix)
    // =========================================================

    /** Compat: valida lista string no contexto CP. */
    public static void assertNoTenantPermissionLeak(List<String> permissions) {
        if (permissions == null) return;

        for (String p : permissions) {
            if (p == null) continue;
            String x = p.trim();
            if (x.isEmpty()) continue;

            if (x.startsWith("TEN_")) {
                throw new DomainException("Permission de Tenant não é permitida no Control Plane: " + x);
            }

            // ✅ também falha se vier sem prefixo
            if (!x.startsWith("CP_")) {
                throw new DomainException("Permission inválida no Control Plane (esperado prefixo CP_): " + x);
            }
        }
    }

    /** Compat: valida lista string no contexto Tenant. */
    public static void assertNoControlPlanePermissionLeak(List<String> permissions) {
        if (permissions == null) return;

        for (String p : permissions) {
            if (p == null) continue;
            String x = p.trim();
            if (x.isEmpty()) continue;

            if (x.startsWith("CP_")) {
                throw new DomainException("Permission de Control Plane não é permitida no Tenant: " + x);
            }

            // ✅ também falha se vier sem prefixo
            if (!x.startsWith("TEN_")) {
                throw new DomainException("Permission inválida no Tenant (esperado prefixo TEN_): " + x);
            }
        }
    }

    public static void assertAllTenantScoped(Collection<String> permissions) {
        assertNoControlPlanePermissionLeak(toList(permissions));
    }

    public static void assertAllControlPlaneScoped(Collection<String> permissions) {
        assertNoTenantPermissionLeak(toList(permissions));
    }

    // =========================================================
    // COMPAT: validate*Strict(Set<EnumPermission>)
    //
    // Seu TenantUserService chama validateTenantPermissionsStrict(Set<TenantPermission>)
    // então mantemos o nome para não sair editando call-sites.
    // =========================================================

    /**
     * Valida permissões tipadas do Tenant (TEN_*), estritamente.
     * - Não aceita CP_*
     * - Não aceita sem prefixo
     */
    public static <T extends Enum<T> & PermissionCode> LinkedHashSet<T> validateTenantPermissionsStrict(Set<T> perms) {
        return normalizeTenantPermissions(perms);
    }

    /**
     * Valida permissões tipadas do ControlPlane (CP_*), estritamente.
     * - Não aceita TEN_*
     * - Não aceita sem prefixo
     */
    public static <T extends Enum<T> & PermissionCode> LinkedHashSet<T> validateControlPlanePermissionsStrict(Set<T> perms) {
        return normalizeControlPlanePermissions(perms);
    }

    private static List<String> toList(Collection<String> c) {
        if (c == null) return List.of();
        return c.stream().filter(Objects::nonNull).toList();
    }
}
