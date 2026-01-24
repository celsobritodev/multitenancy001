package brito.com.multitenancy001.infrastructure.security;

import brito.com.multitenancy001.controlplane.domain.user.ControlPlaneUser;
import brito.com.multitenancy001.shared.security.RoleAuthority;
import brito.com.multitenancy001.tenant.domain.user.TenantUser;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;

@Getter
public class AuthenticatedUserContext implements UserDetails {

    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final String username;
    private final String email;
    private final String password;
    private final boolean mustChangePassword;

    private final boolean enabled;
    private final boolean accountNonLocked;

    private final Long accountId;
    private final String schemaName;

    /**
     * ✅ Role como Enum (type-safe).
     * Ex.: ControlPlaneRole.CONTROLPLANE_OWNER ou TenantRole.TENANT_ADMIN
     * (não entra em authorities)
     */
    private final RoleAuthority roleAuthority;

    /**
     * ✅ Authorities efetivas (permission-only)
     */
    private final Collection<? extends GrantedAuthority> authorities;

    private AuthenticatedUserContext(
            Long userId,
            String username,
            String email,
            String password,
            boolean mustChangePassword,
            boolean enabled,
            boolean accountNonLocked,
            Long accountId,
            String schemaName,
            RoleAuthority role,
            Collection<? extends GrantedAuthority> authorities
    ) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.password = password;
        this.mustChangePassword = mustChangePassword;
        this.enabled = enabled;
        this.accountNonLocked = accountNonLocked;
        this.accountId = accountId;
        this.schemaName = schemaName;
        this.roleAuthority = role;
        this.authorities = authorities;
    }

    /**
     * Compat: onde você precisa da string "ROLE_..."
     * (claims/debug/DTOs). Não armazena string, deriva do Enum.
     */
    public String getRoleAuthority() {
        return roleAuthority == null ? null : roleAuthority.asAuthority();
    }
    
    public String getRoleName() {
        return roleAuthority == null ? null : roleAuthority.toString();
    }


    public static AuthenticatedUserContext fromControlPlaneUser(
            ControlPlaneUser user,
            String schemaName,
            LocalDateTime now,
            Collection<? extends GrantedAuthority> authorities
    ) {
        boolean enabled = user.isEnabledForLogin();
        boolean nonLocked = user.isAccountNonLocked(now);

        return new AuthenticatedUserContext(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPassword(),
                user.isMustChangePassword(),
                enabled,
                nonLocked,
                user.getAccount().getId(),
                schemaName,
                user.getRole(),      // ✅ Enum (ControlPlaneRole)
                authorities
        );
    }

    public static AuthenticatedUserContext fromTenantUser(
            TenantUser user,
            String schemaName,
            LocalDateTime now,
            Collection<? extends GrantedAuthority> authorities
    ) {
        boolean enabled = isTenantEnabledForLogin(user);
        boolean nonLocked = isNonLocked(user.getLockedUntil(), now);

        return new AuthenticatedUserContext(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPassword(),
                user.isMustChangePassword(),
                enabled,
                nonLocked,
                user.getAccountId(),
                schemaName,
                user.getRole(),      // ✅ Enum (TenantRole)
                authorities
        );
    }

    private static boolean isTenantEnabledForLogin(TenantUser user) {
        return !user.isDeleted() && !user.isSuspendedByAccount() && !user.isSuspendedByAdmin();
    }

    private static boolean isNonLocked(LocalDateTime lockedUntil, LocalDateTime now) {
        return lockedUntil == null || !lockedUntil.isAfter(now);
    }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword() { return password; }
    @Override public String getUsername() { return username; }

    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return accountNonLocked; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }
}
