// src/main/java/brito/com/multitenancy001/infrastructure/security/userdetails/TenantUserPrincipal.java
package brito.com.multitenancy001.infrastructure.security.userdetails;

import brito.com.multitenancy001.shared.security.AuthenticatedPrincipal;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.security.TenantPermission;
import brito.com.multitenancy001.tenant.security.TenantRole;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Principal de segurança do Tenant (infra).
 *
 * Regras:
 * - Implementa UserDetails FORA do domínio (DDD limpo).
 * - Usa AppClock como fonte única de tempo (proíbe Instant.now()).
 * - Authorities calculadas aqui (role + permissions).
 */
public final class TenantUserPrincipal implements UserDetails, AuthenticatedPrincipal {

    private static final long serialVersionUID = 1L;

    private final TenantUser user;
    private final AppClock appClock;
    private final Set<GrantedAuthority> authorities;

    public TenantUserPrincipal(TenantUser user, AppClock appClock) {
        /* Constrói o principal do tenant com authorities e clock. */
        this.user = Objects.requireNonNull(user, "user");
        this.appClock = Objects.requireNonNull(appClock, "appClock");
        this.authorities = buildAuthorities(user);
    }

    private static Set<GrantedAuthority> buildAuthorities(TenantUser user) {
        /* Constrói authorities: role + permissions explícitas tipadas. */
        Set<GrantedAuthority> out = new LinkedHashSet<>();

        TenantRole role = user.getRole();
        if (role != null) out.add(new SimpleGrantedAuthority(role.asAuthority()));

        for (TenantPermission p : user.getPermissions()) {
            if (p == null) continue;
            out.add(new SimpleGrantedAuthority(p.asAuthority()));
        }

        return out;
    }

    public TenantUser getUser() {
        return user;
    }

    public Long getAccountId() {
        return user.getAccountId();
    }

    public String getName() {
        return user.getName();
    }

    @Override
    public Long getUserId() {
        return user.getId();
    }

    @Override
    public String getEmail() {
        return user.getEmail();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        /* Authorities do tenant: role + permissions (fail-fast). */
        return authorities;
    }

    @Override
    public String getPassword() {
        /* Campo real da entidade: password (hash). */
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        /* Username = email. */
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        /* Sem expiração por enquanto. */
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        /* Sem expiração por enquanto. */
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        /* Avalia lock usando AppClock (fonte única do tempo). */
        Instant now = appClock.instant();
        return user.isAccountNonLockedAt(now);
    }

    @Override
    public boolean isEnabled() {
        /* Enabled de negócio (sem lock). */
        return user.isEnabledDomain();
    }
}