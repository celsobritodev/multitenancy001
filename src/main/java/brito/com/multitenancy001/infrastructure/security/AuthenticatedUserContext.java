package brito.com.multitenancy001.infrastructure.security;

import brito.com.multitenancy001.controlplane.domain.user.ControlPlaneUser;
import brito.com.multitenancy001.shared.security.RoleAuthority;
import brito.com.multitenancy001.tenant.domain.user.TenantUser;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Getter
public class AuthenticatedUserContext implements UserDetails {

    private static final long serialVersionUID = 1L;

    private final Long userId;
    private final String username;
    private final String email;
    private final String password;

    private final boolean enabled;
    private final boolean accountNonLocked;

    private final Long accountId;
    private final String schemaName;

    // ✅ agora guardamos roleAuthority e permissions (para debug/claims se quiser)
    private final String roleAuthority;
    private final Set<String> permissions;

    // ✅ Authorities finais: ROLE_* + CP_*/TEN_*
    private final Collection<? extends GrantedAuthority> authorities;

    public AuthenticatedUserContext(ControlPlaneUser user, String schemaName, LocalDateTime now) {
        this.userId = user.getId();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.password = user.getPassword();

        this.accountId = user.getAccount().getId();
        this.schemaName = schemaName;

        // ✅ Role
        this.roleAuthority = user.getRole().asAuthority();

        // ✅ Permissions do usuário (precisa existir no entity; se não existir ainda, veja passo 2)
        // Se você tiver permissions como List<String>:
        Collection<String> perms = user.getPermissions();
        this.permissions = toNormalizedSet(perms);

        // ✅ Authorities = ROLE + PERMS
        this.authorities = buildAuthorities(this.roleAuthority, this.permissions);

        this.enabled = user.isEnabledFlag();
        this.accountNonLocked = user.isAccountNonLocked(now);
    }

    public AuthenticatedUserContext(TenantUser user, String schemaName, LocalDateTime now) {
        this.userId = user.getId();
        this.username = user.getUsername();
        this.email = user.getEmail();
        this.password = user.getPassword();

        this.accountId = user.getAccountId();
        this.schemaName = schemaName;

        // ✅ Role
        this.roleAuthority = user.getRole().asAuthority();

        // ✅ permissions já existem no seu TenantUser como List<String>
        this.permissions = toNormalizedSet(user.getPermissions());

        this.authorities = buildAuthorities(this.roleAuthority, this.permissions);

        this.enabled = !user.isDeleted() && !user.isSuspendedByAccount() && !user.isSuspendedByAdmin();
        this.accountNonLocked = user.getLockedUntil() == null || !user.getLockedUntil().isAfter(now);
    }

    private static Set<String> toNormalizedSet(Collection<String> perms) {
        if (perms == null) return Set.of();
        return perms.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static List<GrantedAuthority> buildAuthorities(String roleAuthority, Set<String> permissions) {
        return Stream.concat(
                        Stream.of(roleAuthority),
                        permissions == null ? Stream.empty() : permissions.stream()
                )
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toUnmodifiableList());
    }

    // (Opcional) mantive seu método antigo, mas agora não é mais usado
    @SuppressWarnings("unused")
    private List<GrantedAuthority> mapRolesToAuthorities(RoleAuthority role) {
        return List.of(new SimpleGrantedAuthority(role.asAuthority()));
    }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
    @Override public String getPassword() { return password; }
    @Override public String getUsername() { return username; }

    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return accountNonLocked; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return enabled; }
}
