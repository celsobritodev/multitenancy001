package brito.com.multitenancy001.tenant.users.domain;

import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import brito.com.multitenancy001.shared.domain.audit.AuditInfo;
import brito.com.multitenancy001.shared.domain.audit.Auditable;
import brito.com.multitenancy001.shared.domain.audit.SoftDeletable;
import brito.com.multitenancy001.shared.domain.audit.jpa.AuditEntityListener;
import brito.com.multitenancy001.shared.domain.common.EntityOrigin;
import brito.com.multitenancy001.shared.security.PermissionScopeValidator;
import brito.com.multitenancy001.tenant.security.TenantPermission;
import brito.com.multitenancy001.tenant.security.TenantRole;
import brito.com.multitenancy001.tenant.users.domain.permission.TenantUserPermission;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "tenant_users")
@EntityListeners(AuditEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantUser implements UserDetails, Auditable, SoftDeletable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ==========
    // IDENTITY
    // ==========
    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, columnDefinition = "citext")
    private String email;

    // ==========
    // RBAC
    // ==========
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TenantRole role;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "tenant_user_permissions",
            joinColumns = @JoinColumn(name = "tenant_user_id")
    )
    @Column(name = "permission", nullable = false, length = 120)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Set<TenantPermission> permissions = new LinkedHashSet<>();

    // ==========
    // PROFILE
    // ==========
    @Column(length = 20)
    private String phone;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(length = 50)
    private String timezone;

    @Column(length = 10)
    private String locale;

    // ==========
    // AUTH
    // ==========
    @Column(nullable = false, length = 200)
    private String password;

    @Column(name = "must_change_password", nullable = false)
    @Builder.Default
    private boolean mustChangePassword = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EntityOrigin origin = EntityOrigin.ADMIN;

    // ==========
    // SECURITY
    // ==========
    @Column(name = "last_login", columnDefinition = "TIMESTAMPTZ")
    private Instant lastLoginAt;

    @Column(name = "locked_until", columnDefinition = "TIMESTAMPTZ")
    private Instant lockedUntil;

    @Column(name = "password_changed_at", columnDefinition = "TIMESTAMPTZ")
    private Instant passwordChangedAt;

    @Column(name = "password_reset_token", length = 200)
    private String passwordResetToken;

    @Column(name = "password_reset_expires", columnDefinition = "TIMESTAMPTZ")
    private Instant passwordResetExpiresAt;

    // ==========
    // STATUS
    // ==========
    @Column(name = "suspended_by_account", nullable = false)
    @Builder.Default
    private boolean suspendedByAccount = false;

    @Column(name = "suspended_by_admin", nullable = false)
    @Builder.Default
    private boolean suspendedByAdmin = false;

    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    // ==========
    // AUDIT
    // ==========
    @Embedded
    @Builder.Default
    private AuditInfo audit = new AuditInfo();

    // =========================================================
    // ✅ SEM @PrePersist/@PreUpdate
    // Normalização deve ocorrer na camada de aplicação (services)
    // ou via métodos de domínio abaixo (migração gradual).
    // =========================================================

    // ==========
    // DOMAIN (status)
    // ==========
    public boolean isEnabledDomain() {
        return !deleted && !suspendedByAccount && !suspendedByAdmin;
    }

    public boolean isAccountNonLocked(Instant now) {
        if (now == null) throw new IllegalArgumentException("now is required (use AppClock.instant() in application layer)");
        return lockedUntil == null || !now.isBefore(lockedUntil);
    }

    @Override
    public boolean isAccountNonLocked() {
        throw new IllegalStateException("TenantUser.isAccountNonLocked() without 'now' is forbidden. Use isAccountNonLocked(Instant now) with AppClock.instant() in the application layer.");
    }

    public boolean isEnabledForLogin(Instant now) {
        return isEnabledDomain() && isAccountNonLocked(now);
    }

    // ==========
    // PERMISSIONS (TIPADO)
    // ==========
    public Set<TenantPermission> getPermissions() {
        return permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    public void setPermissions(Set<TenantPermission> permissions) {
        if (this.permissions == null) this.permissions = new LinkedHashSet<>();
        this.permissions.clear();

        if (permissions == null || permissions.isEmpty()) return;

        Set<TenantPermission> validated = PermissionScopeValidator.validateTenantPermissionsStrict(permissions);
        for (TenantPermission p : validated) {
            if (p == null) continue;
            this.permissions.add(p);
        }
    }

    public void grantPermission(TenantPermission permission) {
        if (permission == null) return;
        if (this.permissions == null) this.permissions = new LinkedHashSet<>();

        Set<TenantPermission> tmp = new LinkedHashSet<>(this.permissions);
        tmp.add(permission);

        this.permissions = new LinkedHashSet<>(PermissionScopeValidator.validateTenantPermissionsStrict(tmp));
    }

    public void revokePermission(TenantPermission permission) {
        if (permission == null) return;
        if (this.permissions == null || this.permissions.isEmpty()) return;
        this.permissions.remove(permission);
    }

    /**
     * ✅ Mantido só para leitura/compat (ex.: DTO legado).
     * ❌ Sem setters por code no domínio.
     */
    public Set<TenantUserPermission> getExplicitPermissions() {
        if (permissions == null || permissions.isEmpty()) return Set.of();
        LinkedHashSet<TenantUserPermission> out = new LinkedHashSet<>();
        for (TenantPermission p : permissions) {
            if (p == null) continue;
            out.add(new TenantUserPermission(p.name()));
        }
        return out;
    }

    // ==========
    // Password reset (compat com seus call-sites)
    // ==========
    public void setPasswordResetExpires(Instant expiresAt) {
        this.passwordResetExpiresAt = expiresAt;
    }

    public Instant getPasswordResetExpires() {
        return this.passwordResetExpiresAt;
    }

    // ==========
    // Soft delete
    // ==========
    public void softDelete() {
        this.deleted = true;
    }

    public void softDelete(Instant now, long epochMillis) {
        if (this.deleted) return;
        if (now == null) throw new IllegalArgumentException("now is required");

        this.deleted = true;
        if (this.audit != null) this.audit.markDeleted(now);
    }

    public void restore() {
        this.deleted = false;
        if (this.audit != null) this.audit.clearDeleted();
    }

    public void clearSecurityLockState() {
        this.lockedUntil = null;
    }

    // ==========
    // Optional: helpers de normalização (se você quiser migrar call-sites depois)
    // ==========
    public void rename(String newName) {
        if (newName == null || newName.isBlank()) throw new IllegalArgumentException("name é obrigatório");
        this.name = newName.trim();
    }

    public void changeEmail(String newEmail) {
        String normalized = EmailNormalizer.normalizeOrNull(newEmail);
        if (normalized == null) throw new IllegalArgumentException("email inválido");
        this.email = normalized;
    }

    // ==========
    // Contracts
    // ==========
    @Override
    public boolean isDeleted() {
        return deleted;
    }

    @Override
    public AuditInfo getAudit() {
        return audit;
    }

    // ==========
    // UserDetails
    // ==========
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<GrantedAuthority> out = new LinkedHashSet<>();
        if (role != null) out.add(new SimpleGrantedAuthority(role.asAuthority()));
        for (TenantPermission p : getPermissions()) {
            if (p == null) continue;
            out.add(new SimpleGrantedAuthority(p.name()));
        }
        return out;
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
    public boolean isEnabled() {
        return isEnabledDomain();
    }
}
