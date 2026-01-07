package brito.com.multitenancy001.shared.security;

import brito.com.multitenancy001.controlplane.domain.user.ControlPlaneUser;
import brito.com.multitenancy001.tenant.model.TenantUser;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class AuthenticatedUserContext implements UserDetails {

    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final String username;
    private final String email;
    private final String password;

    // agora é “enabled”
    private final boolean active;

    private final Long accountId;
    private final String schemaName;
    private final Collection<? extends GrantedAuthority> authorities;

    public AuthenticatedUserContext(ControlPlaneUser user, String schemaName) {
        this.userId = user.getId();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.password = user.getPassword();
        this.active = user.isEnabledForLogin();
        this.accountId = user.getAccount().getId();
        this.schemaName = schemaName;
        this.authorities = mapRolesToAuthorities(user.getRole());
    }

    public AuthenticatedUserContext(TenantUser user, String schemaName) {
        this.userId = user.getId();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.password = user.getPassword();
        this.active = user.isEnabledForLogin();
        this.accountId = user.getAccountId();
        this.schemaName = schemaName;
        this.authorities = mapRolesToAuthorities(user.getRole());
    }


    private List<GrantedAuthority> mapRolesToAuthorities(RoleAuthority role) {
        return List.of(new SimpleGrantedAuthority(role.asAuthority()));
    }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword() { return password; }
    @Override public String getUsername() { return username; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return active; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return active; }
}
