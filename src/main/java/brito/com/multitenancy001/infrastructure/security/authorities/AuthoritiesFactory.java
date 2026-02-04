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

        for (ControlPlanePermission p : ControlPlaneRolePermissions.permissionsFor(user.getRole())) {
            merged.add(p.name());
        }

        if (user.getPermissions() != null) {
            for (ControlPlanePermission p : user.getPermissions()) {
                if (p == null) continue;
                merged.add(p.name());
            }
        }

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

        // permissions explícitas do banco (JÁ É String code)
        if (user.getPermissions() != null) {
            for (String code : user.getPermissions()) {
                if (code == null || code.isBlank()) continue;
                merged.add(code.trim().toUpperCase(Locale.ROOT));
            }
        }

        merged = PermissionScopeValidator.normalizeTenantStrict(merged);

        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        for (String perm : merged) {
            if (perm == null || perm.isBlank()) continue;
            authorities.add(new SimpleGrantedAuthority(perm.trim().toUpperCase(Locale.ROOT)));
        }
        return authorities;
    }
}
