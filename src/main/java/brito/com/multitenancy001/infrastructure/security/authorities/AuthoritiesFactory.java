package brito.com.multitenancy001.infrastructure.security.authorities;

import brito.com.multitenancy001.controlplane.security.ControlPlanePermission;
import brito.com.multitenancy001.controlplane.security.ControlPlaneRolePermissions;
import brito.com.multitenancy001.controlplane.users.domain.ControlPlaneUser;
import brito.com.multitenancy001.shared.security.PermissionScopeValidator;
import brito.com.multitenancy001.tenant.security.TenantPermission;
import brito.com.multitenancy001.tenant.security.TenantRolePermissions;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

public final class AuthoritiesFactory {

    private AuthoritiesFactory() {}

    public static Set<GrantedAuthority> forControlPlane(ControlPlaneUser user) {
        Set<String> merged = new LinkedHashSet<>();

        // defaults da role (enum -> string)
        for (ControlPlanePermission p : ControlPlaneRolePermissions.permissionsFor(user.getRole())) {
            merged.add(p.name());
        }

        // permissions explícitas do banco (enum -> string)
        if (user.getPermissions() != null) {
            for (ControlPlanePermission p : user.getPermissions()) {
                if (p == null) continue;
                merged.add(p.name());
            }
        }

        // normaliza e bloqueia escopo errado (TEN_ dentro do CP, etc.)
        merged = PermissionScopeValidator.normalizeControlPlaneStrict(merged);

        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        for (String perm : merged) {
            if (perm == null || perm.isBlank()) continue;
            authorities.add(new SimpleGrantedAuthority(perm.trim().toUpperCase(Locale.ROOT)));
        }
        return authorities;
    }

    public static Set<GrantedAuthority> forTenant(TenantUser user) {
        Set<String> merged = new LinkedHashSet<>();

        // defaults por role
        if (user.getRole() != null) {
            for (TenantPermission p : TenantRolePermissions.permissionsFor(user.getRole())) {
                merged.add(p.name());
            }
        }

        // permissions explícitas do banco (enum -> string)
        if (user.getPermissions() != null) {
            for (TenantPermission p : user.getPermissions()) {
                if (p == null) continue;
                merged.add(p.name());
            }
        }

        // normaliza SEMPRE e bloqueia CP_ dentro do tenant
        merged = PermissionScopeValidator.normalizeTenantStrict(merged);

        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        for (String perm : merged) {
            if (perm == null || perm.isBlank()) continue;
            authorities.add(new SimpleGrantedAuthority(perm.trim().toUpperCase(Locale.ROOT)));
        }
        return authorities;
    }
}
