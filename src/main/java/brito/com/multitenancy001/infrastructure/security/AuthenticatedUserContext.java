package brito.com.multitenancy001.infrastructure.security;

import brito.com.multitenancy001.controlplane.users.domain.ControlPlaneUser;
import brito.com.multitenancy001.shared.security.AuthenticatedPrincipal;
import brito.com.multitenancy001.shared.security.RoleAuthority;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;

/**
 * Principal autenticado usado pelo Spring Security.
 *
 * Observação importante:
 * - O método do contrato UserDetails se chama getUsername().
 * - No nosso domínio, login é por email.
 * - Por isso, aqui guardamos o principal como principalEmail e
 *   implementamos getUsername() retornando esse email.
 */
public class AuthenticatedUserContext implements UserDetails, AuthenticatedPrincipal {

    private static final long serialVersionUID = 1L;

    private final Long userId;

    /** Email usado como principal (UserDetails#getUsername). */
    private final String principalEmail;

    private final String name;
    private final String email;

    private final String password;
    private final boolean mustChangePassword;

    private final boolean enabled;
    private final boolean accountNonLocked;

    private final Long accountId;
    private final String schemaName;

    private final boolean suspendedByAccount;
    private final boolean suspendedByAdmin;
    private final boolean deleted;

    private final RoleAuthority roleAuthority;

    private final Collection<? extends GrantedAuthority> authorities;

    private AuthenticatedUserContext(
            Long userId,
            String principalEmail,
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
            RoleAuthority roleAuthority,
            Collection<? extends GrantedAuthority> authorities
    ) {
        this.userId = userId;
        this.principalEmail = principalEmail;
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
        this.roleAuthority = roleAuthority;
        this.authorities = authorities;
    }

    @Override
    public Long getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    /**
     * Email principal usado para autenticação (mesmo valor de getUsername()).
     * Exposto por clareza.
     */
    public String getPrincipalEmail() {
        return principalEmail;
    }

    /**
     * Contrato do AuthenticatedPrincipal.
     */
    @Override
    public String getEmail() {
        return email;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getSchemaName() {
        return schemaName;
    }

    public boolean isSuspendedByAccount() {
        return suspendedByAccount;
    }

    public boolean isSuspendedByAdmin() {
        return suspendedByAdmin;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public RoleAuthority getRoleAuthorityEnum() {
        return roleAuthority;
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
                email,
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
                user.getRole(),
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
                email,
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
                user.getRole(),
                authorities
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    /**
     * Contrato do Spring Security. No nosso sistema, isso é SEMPRE o email.
     */
    @Override
    public String getUsername() {
        return principalEmail;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
