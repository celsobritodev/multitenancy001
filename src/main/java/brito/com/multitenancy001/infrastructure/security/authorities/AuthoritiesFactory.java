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
import java.util.Set;

public final class AuthoritiesFactory {

    private AuthoritiesFactory() {}

    public static Set<GrantedAuthority> forControlPlane(ControlPlaneUser user) {
        if (user == null) return Set.of();

        // 1) PERMISSIONS (strings)
        Set<String> mergedPerms = new LinkedHashSet<>();

        // defaults por role
        for (ControlPlanePermission p : ControlPlaneRolePermissions.permissionsFor(user.getRole())) {
            if (p == null) continue;
            mergedPerms.add(p.asAuthority());
        }

        // permissões explícitas do usuário
        if (user.getPermissions() != null) {
            for (ControlPlanePermission p : user.getPermissions()) {
                if (p == null) continue;
                mergedPerms.add(p.asAuthority());
            }
        }

        // fail-fast + normalize CP_
        mergedPerms = PermissionScopeValidator.normalizeControlPlaneStrict(mergedPerms);

        // 2) BUILD authorities (permissions + role)
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();

        // role como authority (ROLE_...)
        if (user.getRole() != null) {
            authorities.add(new SimpleGrantedAuthority(user.getRole().asAuthority()));
        }

        // permissions
        for (String perm : mergedPerms) {
            if (perm == null || perm.isBlank()) continue;
            authorities.add(new SimpleGrantedAuthority(perm.trim()));
        }

        return authorities;
    }

    public static Set<GrantedAuthority> forTenant(TenantUser user) {
        if (user == null) return Set.of();

        // 1) PERMISSIONS (tipadas)
        Set<TenantPermission> mergedPerms = new LinkedHashSet<>();

        // defaults por role
        if (user.getRole() != null) {
            mergedPerms.addAll(TenantRolePermissions.permissionsFor(user.getRole()));
        }

        // permissões explícitas do usuário
        if (user.getPermissions() != null) {
            mergedPerms.addAll(user.getPermissions());
        }

        // fail-fast tipado (não deixa sair do escopo Tenant)
        mergedPerms = PermissionScopeValidator.validateTenantPermissionsStrict(mergedPerms);

        // 2) BUILD authorities (permissions + role)
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();

        // role como authority (ROLE_...)
        if (user.getRole() != null) {
            authorities.add(new SimpleGrantedAuthority(user.getRole().asAuthority()));
        }

        // permissions
        for (TenantPermission p : mergedPerms) {
            if (p == null) continue;
            authorities.add(new SimpleGrantedAuthority(p.asAuthority()));
        }

        return authorities;
    }
}
