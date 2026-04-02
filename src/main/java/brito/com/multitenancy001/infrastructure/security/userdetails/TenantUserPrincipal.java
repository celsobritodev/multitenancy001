package brito.com.multitenancy001.infrastructure.security.userdetails;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import brito.com.multitenancy001.shared.security.AuthenticatedPrincipal;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.security.TenantPermission;
import brito.com.multitenancy001.tenant.security.TenantRole;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Principal de segurança do tenant.
 *
 * <p>Regras:</p>
 * <ul>
 *   <li>Implementa {@link UserDetails} fora do domínio.</li>
 *   <li>Implementa {@link AuthenticatedPrincipal} para compatibilidade com o contrato de autenticação do projeto.</li>
 *   <li>Usa {@link AppClock} como fonte única de tempo.</li>
 *   <li>Calcula authorities a partir de role + permissions.</li>
 * </ul>
 */
public final class TenantUserPrincipal implements UserDetails, AuthenticatedPrincipal {

    private static final long serialVersionUID = 1L;

    private final TenantUser user;
    private final AppClock appClock;
    private final Set<GrantedAuthority> authorities;

    public TenantUserPrincipal(TenantUser user, AppClock appClock) {
        this.user = Objects.requireNonNull(user, "user");
        this.appClock = Objects.requireNonNull(appClock, "appClock");
        this.authorities = buildAuthorities(user);
    }

    /**
     * Retorna a entidade de domínio subjacente.
     *
     * @return usuário tenant de domínio
     */
    public TenantUser domainUser() {
        return user;
    }

    /**
     * Retorna o id interno do usuário tenant.
     *
     * @return id do usuário
     */
    public Long getId() {
        return user.getId();
    }

    /**
     * Implementação do contrato de AuthenticatedPrincipal.
     *
     * @return id do usuário autenticado
     */
    @Override
    public Long getUserId() {
        return user.getId();
    }

    /**
     * Retorna o id da conta do usuário.
     *
     * @return account id
     */
    public Long getAccountId() {
        return user.getAccountId();
    }

    /**
     * Retorna o email do tenant.
     *
     * @return email do usuário
     */
    public String getTenantEmail() {
        return user.getEmail();
    }

    /**
     * Implementação do contrato de AuthenticatedPrincipal.
     *
     * @return email do usuário autenticado
     */
    @Override
    public String getEmail() {
        return user.getEmail();
    }

    /**
     * Retorna role tenant.
     *
     * @return role do tenant
     */
    public TenantRole getTenantRole() {
        return user.getRole();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        Instant now = appClock.instant();
        return user.isAccountNonLockedAt(now);
    }

    @Override
    public boolean isEnabled() {
        return user.isEnabledDomain();
    }

    /**
     * Monta authorities a partir da role e permissões explícitas.
     *
     * @param user usuário tenant
     * @return conjunto de authorities
     */
    private static Set<GrantedAuthority> buildAuthorities(TenantUser user) {
        Set<GrantedAuthority> auth = new LinkedHashSet<>();

        TenantRole role = user.getRole();
        if (role != null) {
            auth.add(new SimpleGrantedAuthority(role.asAuthority()));
        }

        if (user.getPermissions() != null) {
            for (TenantPermission permission : user.getPermissions()) {
                if (permission == null) {
                    continue;
                }
                auth.add(new SimpleGrantedAuthority(permission.asAuthority()));
            }
        }

        return auth;
    }
}