package brito.com.multitenancy001.infrastructure.security;

import brito.com.multitenancy001.controlplane.domain.user.ControlPlaneUser;
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

    // opcional: manter role para debug/claims (NÃO entra em authorities)
    private final String roleAuthority;

    // ✅ permission-only
    private final Collection<? extends GrantedAuthority> authorities;
    
    public boolean isMustChangePassword() {
        return mustChangePassword;
    }
    

    public AuthenticatedUserContext(
            ControlPlaneUser user,
            String schemaName,
            LocalDateTime now,
            Collection<? extends GrantedAuthority> authorities
    ) {
        this.userId = user.getId();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.password = user.getPassword();

        this.accountId = user.getAccount().getId();
        this.schemaName = schemaName;

        this.roleAuthority = user.getRole() != null ? user.getRole().asAuthority() : null;

        this.authorities = authorities;

        this.enabled = user.isEnabledForLogin();
        this.accountNonLocked = user.isAccountNonLocked(now);
        this.mustChangePassword = user.isMustChangePassword();

    }

    public AuthenticatedUserContext(
            TenantUser user,
            String schemaName,
            LocalDateTime now,
            Collection<? extends GrantedAuthority> authorities
    ) {
        this.userId = user.getId();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.password = user.getPassword();

        this.accountId = user.getAccountId();
        this.schemaName = schemaName;

        this.roleAuthority = user.getRole() != null ? user.getRole().asAuthority() : null;

        this.authorities = authorities;

        this.enabled = !user.isDeleted() && !user.isSuspendedByAccount() && !user.isSuspendedByAdmin();
        this.accountNonLocked = user.getLockedUntil() == null || !user.getLockedUntil().isAfter(now);
        this.mustChangePassword = user.isMustChangePassword();

    }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword() { return password; }
    @Override public String getUsername() { return username; }

    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return accountNonLocked; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }
}
