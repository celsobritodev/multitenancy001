// src/main/java/brito/com/multitenancy001/infrastructure/security/AuthenticatedUserContext.java
package brito.com.multitenancy001.infrastructure.security;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.security.TenantPermission;
import brito.com.multitenancy001.tenant.security.TenantRole;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.time.Instant;
import java.util.*;

/**
 * Principal autenticado do sistema (infra).
 *
 * Regras:
 * - Este é o "principal" canônico usado por JWT/filters/token provider (compat com o projeto atual).
 * - NÃO depende de TenantUser como UserDetails (domínio puro).
 * - NÃO usa Instant.now() (qualquer decisão temporal recebe "now" por parâmetro).
 * - Exponibiliza getters que o projeto já utiliza (JwtTokenProvider, SecurityUtils, MustChangePasswordFilter etc.).
 *
 * Observação:
 * - Esta classe existe para compatibilidade do stack atual.
 * - O caminho futuro é evoluir gradualmente para principals específicos (TenantUserPrincipal/ControlPlaneUserPrincipal),
 *   mas sem quebrar o resto do security stack no meio.
 */
@Getter
public final class AuthenticatedUserContext implements UserDetails {

    @Serial
    private static final long serialVersionUID = 1L;

    // ==========
    // Identity
    // ==========
    private final Long userId;
    private final String email;

    /**
     * Para tenant: accountId/tenantSchema preenchidos.
     * Para control plane: podem ser null.
     */
    private final Long accountId;
    private final String tenantSchema;

    // ==========
    // Role / Authorities
    // ==========
    private final String roleName;       // ex: TENANT_OWNER / CONTROLPLANE_ADMIN
    private final String roleAuthority;  // ex: ROLE_TENANT_OWNER / ROLE_CONTROLPLANE_ADMIN
    private final Set<GrantedAuthority> authorities;

    // ==========
    // Security flags
    // ==========
    private final boolean enabled;
    private final boolean accountNonLocked;
    private final boolean mustChangePassword;

    /**
     * Hash da senha (quando aplicável em fluxos que dependem disso).
     * Pode ser null em refresh/jwt auth dependendo do fluxo.
     */
    private final String passwordHashOrNull;

    private AuthenticatedUserContext(
            Long userId,
            String email,
            Long accountId,
            String tenantSchema,
            String roleName,
            String roleAuthority,
            Set<GrantedAuthority> authorities,
            boolean enabled,
            boolean accountNonLocked,
            boolean mustChangePassword,
            String passwordHashOrNull
    ) {
        /* Constrói o contexto autenticado. */
        this.userId = Objects.requireNonNull(userId, "userId");
        this.email = Objects.requireNonNull(email, "email");
        this.accountId = accountId;
        this.tenantSchema = tenantSchema;
        this.roleName = roleName;
        this.roleAuthority = roleAuthority;
        this.authorities = Collections.unmodifiableSet(new LinkedHashSet<>(authorities == null ? Set.of() : authorities));
        this.enabled = enabled;
        this.accountNonLocked = accountNonLocked;
        this.mustChangePassword = mustChangePassword;
        this.passwordHashOrNull = passwordHashOrNull;
    }

    // ==========
    // Factories (Tenant)
    // ==========

    /**
     * Factory usado pelos flows antigos do tenant (ex.: TenantAuthMechanicsSpringSecurity).
     *
     * @param user entidade de domínio do tenant (DDD puro)
     * @param tenantSchema schema do tenant (string)
     * @param now instante (vem do AppClock)
     * @param authorities authorities já calculadas (role + perms)
     */
    public static AuthenticatedUserContext fromTenantUser(
            TenantUser user,
            String tenantSchema,
            Instant now,
            Set<GrantedAuthority> authorities
    ) {
        /* Constrói AuthenticatedUserContext para TenantUser, sem Instant.now(). */
        Objects.requireNonNull(user, "user");
        Objects.requireNonNull(now, "now");

        String email = Objects.requireNonNullElse(user.getEmail(), "");
        Long userId = Objects.requireNonNull(user.getId(), "tenant user.id");
        Long accountId = Objects.requireNonNull(user.getAccountId(), "tenant user.accountId");

        TenantRole role = user.getRole();
        String roleName = role != null ? role.name() : null;
        String roleAuthority = role != null ? role.asAuthority() : null;

        boolean enabled = user.isEnabledDomain();
        boolean accountNonLocked = user.isAccountNonLockedAt(now);
        boolean mustChangePassword = user.isMustChangePassword();

        Set<GrantedAuthority> auth = new LinkedHashSet<>();
        if (authorities != null) auth.addAll(authorities);

        // fallback de authorities se chamador não passou
        if (auth.isEmpty()) {
            if (roleAuthority != null) auth.add(new SimpleGrantedAuthority(roleAuthority));
            for (TenantPermission p : user.getPermissions()) {
                if (p == null) continue;
                auth.add(new SimpleGrantedAuthority(p.asAuthority()));
            }
        }

        return new AuthenticatedUserContext(
                userId,
                email,
                accountId,
                tenantSchema,
                roleName,
                roleAuthority,
                auth,
                enabled,
                accountNonLocked,
                mustChangePassword,
                user.getPassword() // hash (campo real)
        );
    }

    /**
     * Overload de compat (alguns call-sites passam Collection).
     */
    public static AuthenticatedUserContext fromTenantUser(
            TenantUser user,
            String tenantSchema,
            Instant now,
            Collection<? extends GrantedAuthority> authorities
    ) {
        /* Compat overload: Collection -> Set. */
        Set<GrantedAuthority> set = new LinkedHashSet<>();
        if (authorities != null) set.addAll(authorities);
        return fromTenantUser(user, tenantSchema, now, set);
    }

    // ==========
    // Factories (Control Plane)
    // ==========
    /**
     * Factory genérico para Control Plane (sem depender de entidade específica aqui).
     * Use em refresh/jwt parsing quando você já tem claims.
     */
    public static AuthenticatedUserContext fromControlPlaneClaims(
            Long userId,
            String email,
            String roleName,
            String roleAuthority,
            Set<GrantedAuthority> authorities
    ) {
        /* Constrói contexto do Control Plane sem entidade e sem Instant.now(). */
        return new AuthenticatedUserContext(
                Objects.requireNonNull(userId, "userId"),
                Objects.requireNonNull(email, "email"),
                null,
                null,
                roleName,
                roleAuthority,
                authorities == null ? Set.of() : authorities,
                true,
                true,
                false,
                null
        );
    }

    // ==========
    // Helpers usados por filtros/services
    // ==========

    public void assertEnabledForLogin() {
        /* Fail-fast para fluxos de login/refresh quando necessário. */
        if (!enabled) {
            throw new ApiException(ApiErrorCode.USER_NOT_ENABLED, "Usuário não está habilitado");
        }
        if (!accountNonLocked) {
            // Não assumo que exista um ApiErrorCode específico; reaproveito USER_NOT_ENABLED.
            throw new ApiException(ApiErrorCode.USER_NOT_ENABLED, "Usuário está bloqueado");
        }
    }

    // ==========
    // UserDetails
    // ==========
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHashOrNull;
    }

    @Override
    public String getUsername() {
        return email;
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
        return accountNonLocked;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}