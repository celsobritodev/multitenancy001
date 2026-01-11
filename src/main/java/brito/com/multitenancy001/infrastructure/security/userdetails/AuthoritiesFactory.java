package brito.com.multitenancy001.infrastructure.security.userdetails;

import brito.com.multitenancy001.controlplane.domain.security.ControlPlaneRolePermissions;
import brito.com.multitenancy001.controlplane.domain.user.ControlPlaneUser;
import brito.com.multitenancy001.shared.security.PermissionNormalizer;
import brito.com.multitenancy001.tenant.domain.security.TenantRolePermissions;
import brito.com.multitenancy001.tenant.domain.user.TenantUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

public final class AuthoritiesFactory {

    private AuthoritiesFactory() {}

    public static Collection<? extends GrantedAuthority> forControlPlane(ControlPlaneUser user) {
        Set<String> perms = new LinkedHashSet<>();

        // 1) role -> permissions (enum -> name)
        ControlPlaneRolePermissions.permissionsFor(user.getRole())
                .forEach(p -> perms.add(p.name()));

        // 2) permissions explícitas do user (Set<String>)
        if (user.getPermissions() != null) {
            perms.addAll(user.getPermissions());
        }

        // 3) normaliza CP_ e bloqueia TEN_
        Set<String> normalized = PermissionNormalizer.normalizeControlPlane(perms);

        return normalized.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }

    public static Collection<? extends GrantedAuthority> forTenant(TenantUser user) {
        Set<String> permissions = new LinkedHashSet<>();

        // 1) role -> permissions (enum -> name)
        TenantRolePermissions.permissionsFor(user.getRole())
                .forEach(p -> permissions.add(p.name()));

        // 2) permissions explícitas do user (List<String>)
        if (user.getPermissions() != null) {
            permissions.addAll(user.getPermissions());
        }

        // 3) normaliza TEN_ e bloqueia CP_
        Set<String> normalized = PermissionNormalizer.normalizeTenant(permissions);

        return normalized.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }
}
