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
        // ✅ 100% tipado até o final
        Set<TenantPermission> merged = new LinkedHashSet<>();

        // defaults por role
        if (user.getRole() != null) {
            merged.addAll(TenantRolePermissions.permissionsFor(user.getRole()));
        }

        // permissions explícitas do usuário (tipadas)
        if (user.getPermissions() != null) {
            merged.addAll(user.getPermissions());
        }

        // ✅ FAIL-FAST tipado (não deixa permissão fora do escopo Tenant)
        merged = PermissionScopeValidator.validateTenantPermissionsStrict(merged);

        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        for (TenantPermission p : merged) {
            if (p == null) continue;
            authorities.add(new SimpleGrantedAuthority(p.name().trim().toUpperCase(Locale.ROOT)));
        }
        return authorities;
    }
}
