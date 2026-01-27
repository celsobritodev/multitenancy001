package brito.com.multitenancy001.infrastructure.security;

import brito.com.multitenancy001.controlplane.domain.user.ControlPlaneUser;
import brito.com.multitenancy001.shared.security.RoleAuthority;
import brito.com.multitenancy001.tenant.domain.user.TenantUser;
import lombok.Getter;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;

@Getter
public class AuthenticatedUserContext implements UserDetails {

    private static final long serialVersionUID = 1L;

    private final Long userId;

    /**
     * Principal do Spring Security (UserDetails.getUsername()).
     * ✅ Sempre email.
     */
    private final String username;

    private final String name;
    private final String email;

    private final String password;
    private final boolean mustChangePassword;

    private final boolean enabled;
    private final boolean accountNonLocked;

    private final Long accountId;
    private final String schemaName;

    // ✅ flags usadas no /me
    private final boolean suspendedByAccount;
    private final boolean suspendedByAdmin;
    private final boolean deleted;

    /**
     * ✅ Role como Enum (type-safe).
     */
    private final RoleAuthority roleAuthority;

    /**
     * ✅ Authorities efetivas (permission-only).
     */
    private final Collection<? extends GrantedAuthority> authorities;

    private AuthenticatedUserContext(
            Long userId,
            String username,
            String name,
            String email,
            String password,
            boolean mustChangePassword,
            boolean enabled,
            boolean accountNonLocked,
            Long accountId,
            String schemaName,
            boolean suspendedByAccount,
            boolean suspendedByAdmin,
            boolean deleted,
            RoleAuthority role,
            Collection<? extends GrantedAuthority> authorities
    ) {
        this.userId = userId;
        this.username = username;
        this.name = name;
        this.email = email;
        this.password = password;
        this.mustChangePassword = mustChangePassword;
        this.enabled = enabled;
        this.accountNonLocked = accountNonLocked;
        this.accountId = accountId;
        this.schemaName = schemaName;
        this.suspendedByAccount = suspendedByAccount;
        this.suspendedByAdmin = suspendedByAdmin;
        this.deleted = deleted;
        this.roleAuthority = role;
        this.authorities = authorities;
    }

    public String getRoleAuthority() {
        return roleAuthority == null ? null : roleAuthority.asAuthority();
    }

    public String getRoleName() {
        return roleAuthority == null ? null : roleAuthority.toString();
    }

    /** Compat com código antigo que chamava getRole() */
    public String getRole() {
        return getRoleName();
    }

    public static AuthenticatedUserContext fromTenantUser(
            TenantUser user,
            String tenantSchema,
            LocalDateTime now,
            Collection<? extends GrantedAuthority> authorities
    ) {
        String email = user.getEmail();

        if (!user.isEnabledForLogin(now)) {
            throw new BadCredentialsException("USER_DISABLED");
        }

        return new AuthenticatedUserContext(
                user.getId(),
                email,                 // username = email (principal)
                user.getName(),
                email,
                user.getPassword(),
                user.isMustChangePassword(),
                user.isEnabled(),
                user.isAccountNonLocked(now),
                user.getAccountId(),
                tenantSchema,
                user.isSuspendedByAccount(),
                user.isSuspendedByAdmin(),
                user.isDeleted(),
                user.getRole(),        // ✅ TenantRole implements RoleAuthority
                authorities
        );
    }

    public static AuthenticatedUserContext fromControlPlaneUser(
            ControlPlaneUser user,
            String tenantSchema,
            LocalDateTime now,
            Collection<? extends GrantedAuthority> authorities
    ) {
        String email = user.getEmail();

        if (!user.isEnabledForLogin(now)) {
            throw new BadCredentialsException("USER_DISABLED");
        }

        return new AuthenticatedUserContext(
                user.getId(),
                email,                 // username = email (principal)
                user.getName(),
                email,
                user.getPassword(),
                user.isMustChangePassword(),
                user.isEnabled(),
                user.isAccountNonLocked(now),
                user.getAccount().getId(),
                tenantSchema,
                user.isSuspendedByAccount(),
                user.isSuspendedByAdmin(),
                user.isDeleted(),
                user.getRole(),        // ✅ ControlPlaneRole implements RoleAuthority
                authorities
        );
    }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword() { return password; }
    @Override public String getUsername() { return username; }

    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return accountNonLocked; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }
}
