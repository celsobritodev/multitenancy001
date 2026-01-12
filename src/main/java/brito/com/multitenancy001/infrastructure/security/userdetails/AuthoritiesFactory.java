package brito.com.multitenancy001.infrastructure.security.userdetails;

import brito.com.multitenancy001.controlplane.domain.user.ControlPlaneUser;
import brito.com.multitenancy001.controlplane.security.ControlPlaneRolePermissions;
import brito.com.multitenancy001.shared.security.PermissionScopeValidator;
import brito.com.multitenancy001.tenant.domain.user.TenantUser;
import brito.com.multitenancy001.tenant.security.TenantRolePermissions;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.LinkedHashSet;

public final class AuthoritiesFactory {

    private AuthoritiesFactory() {}

    public static Collection<? extends GrantedAuthority> forControlPlane(ControlPlaneUser user) {
    	LinkedHashSet<String> permissions = new LinkedHashSet<>();

        // 1) role -> permissions (enum -> name)
        ControlPlaneRolePermissions.permissionsFor(user.getRole())
                .forEach(p -> permissions.add(p.name()));

        // 2) permissions explícitas do user
        if (user.getPermissions() != null) {
            permissions.addAll(user.getPermissions());
        }

        // 3) normaliza CP_ e bloqueia TEN_
        LinkedHashSet<String> normalized = PermissionScopeValidator.normalizeControlPlane(permissions);

        return normalized.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }

    public static Collection<? extends GrantedAuthority> forTenant(TenantUser user) {
    	LinkedHashSet<String> permissions = new LinkedHashSet<>();

        // 1) role -> permissions (enum -> name)
        TenantRolePermissions.permissionsFor(user.getRole())
                .forEach(p -> permissions.add(p.name()));

        // 2) permissions explícitas do user (List<String>)
        if (user.getPermissions() != null) {
            permissions.addAll(user.getPermissions());
        }

        // 3) normaliza TEN_ e bloqueia CP_
        LinkedHashSet<String> normalized = PermissionScopeValidator.normalizeTenant(permissions);

        return normalized.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }
}
