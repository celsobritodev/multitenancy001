package brito.com.multitenancy001.infrastructure.security.userdetails;

import brito.com.multitenancy001.controlplane.domain.user.ControlPlaneUser;
import brito.com.multitenancy001.controlplane.security.ControlPlanePermission;
import brito.com.multitenancy001.controlplane.security.ControlPlaneRolePermissions;
import brito.com.multitenancy001.shared.security.PermissionScopeValidator;
import brito.com.multitenancy001.tenant.domain.user.TenantUser;
import brito.com.multitenancy001.tenant.security.TenantPermission;
import brito.com.multitenancy001.tenant.security.TenantRolePermissions;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.util.StringUtils;

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

        // permissions explícitas do banco
        if (user.getPermissions() != null) {
            for (String raw : user.getPermissions()) {
                if (!StringUtils.hasText(raw)) continue;
                merged.add(raw.trim().toUpperCase(Locale.ROOT));
            }
        }

        // normaliza e bloqueia escopo errado (TEN_ dentro do CP, etc.)
        merged = PermissionScopeValidator.normalizeControlPlane(merged);

        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        for (String perm : merged) {
            authorities.add(new SimpleGrantedAuthority(perm));
        }
        return authorities;
    }

    public static Set<GrantedAuthority> forTenant(TenantUser user) {
        Set<String> merged = new LinkedHashSet<>();

        // ✅ defaults por role (igual sua entidade faz)
        if (user.getRole() != null) {
            for (TenantPermission p : TenantRolePermissions.permissionsFor(user.getRole())) {
                merged.add(p.name());
            }
        }

        // permissions explícitas do banco
        if (user.getPermissions() != null) {
            for (String raw : user.getPermissions()) {
                if (!StringUtils.hasText(raw)) continue;
                merged.add(raw.trim().toUpperCase(Locale.ROOT));
            }
        }

        // ✅ normaliza SEMPRE e bloqueia CP_ dentro do tenant
        merged = PermissionScopeValidator.normalizeTenant(merged);

        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        for (String perm : merged) {
            authorities.add(new SimpleGrantedAuthority(perm));
        }
        return authorities;
    }
}
