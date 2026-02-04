package brito.com.multitenancy001.tenant.users.domain;

import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import brito.com.multitenancy001.shared.domain.audit.AuditInfo;
import brito.com.multitenancy001.shared.domain.audit.Auditable;
import brito.com.multitenancy001.shared.domain.audit.SoftDeletable;
import brito.com.multitenancy001.shared.domain.audit.jpa.AuditEntityListener;
import brito.com.multitenancy001.shared.domain.common.EntityOrigin;
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
import java.util.Locale;
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

    // ✅ Alinhado com migration: email CITEXT NOT NULL
    @Column(nullable = false, columnDefinition = "citext")
    private String email;

    // ==========
    // RBAC
    // ==========
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TenantRole role;

    /**
     * Persistência: codes (String) via ElementCollection.
     *
     * Migration (Flyway) criou:
     * - tabela: tenant_user_permissions
     * - FK: tenant_user_id
     * - coluna: permission VARCHAR(120)
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "tenant_user_permissions",
            joinColumns = @JoinColumn(name = "tenant_user_id")
    )
    @Column(name = "permission", nullable = false, length = 120)
    @Builder.Default
    private Set<String> permissionCodes = new LinkedHashSet<>();

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
    private boolean mustChangePassword = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EntityOrigin origin = EntityOrigin.ADMIN;

    // ==========
    // SECURITY (instantes reais => Instant)
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
    // AUDIT (fonte única)
    // ==========
    @Embedded
    @Builder.Default
    private AuditInfo audit = new AuditInfo();

    // ==========
    // NORMALIZATION
    // ==========
    @PrePersist
    @PreUpdate
    private void normalize() {
        this.email = EmailNormalizer.normalizeOrNull(this.email);
        if (this.name != null) this.name = this.name.trim();
        if (this.phone != null) this.phone = this.phone.trim();
        if (this.avatarUrl != null) this.avatarUrl = this.avatarUrl.trim();
        if (this.timezone != null) this.timezone = this.timezone.trim();
        if (this.locale != null) this.locale = this.locale.trim();

        if (this.permissionCodes != null && !this.permissionCodes.isEmpty()) {
            LinkedHashSet<String> normalized = new LinkedHashSet<>();
            for (String c : this.permissionCodes) {
                if (c == null || c.isBlank()) continue;
                normalized.add(c.trim().toUpperCase(Locale.ROOT));
            }
            this.permissionCodes = normalized;
        }
    }

    // ==========
    // DOMAIN (status)
    // ==========
    public boolean isEnabledDomain() {
        return !deleted && !suspendedByAccount && !suspendedByAdmin;
    }

    public boolean isAccountNonLocked(Instant now) {
        if (now == null) now = Instant.now();
        return lockedUntil == null || !now.isBefore(lockedUntil);
    }

    @Override
    public boolean isAccountNonLocked() {
        return isAccountNonLocked(Instant.now());
    }

    public boolean isEnabledForLogin(Instant now) {
        return isEnabledDomain() && isAccountNonLocked(now);
    }

    // ==========
    // Explicit permissions API (domínio manda)
    // ==========
    public Set<String> getPermissionCodes() {
        return permissionCodes == null ? Set.of() : Set.copyOf(permissionCodes);
    }

    /** Alias compat com código antigo (AuthoritiesFactory usa getPermissions()) */
    public Set<String> getPermissions() {
        return getPermissionCodes();
    }

    /** ✅ COMPAT: converte enum -> codes */
    public void setPermissions(Set<TenantPermission> permissions) {
        this.permissionCodes.clear();
        if (permissions == null || permissions.isEmpty()) return;
        for (TenantPermission p : permissions) {
            if (p == null) continue;
            this.permissionCodes.add(p.name());
        }
    }

    public void setPermissionsFromCodes(Set<String> codes) {
        this.permissionCodes.clear();
        if (codes == null || codes.isEmpty()) return;
        for (String c : codes) {
            if (c == null || c.isBlank()) continue;
            this.permissionCodes.add(c.trim().toUpperCase(Locale.ROOT));
        }
    }

    public Set<TenantUserPermission> getExplicitPermissions() {
        if (permissionCodes == null || permissionCodes.isEmpty()) return Set.of();
        LinkedHashSet<TenantUserPermission> out = new LinkedHashSet<>();
        for (String code : permissionCodes) {
            if (code == null || code.isBlank()) continue;
            out.add(new TenantUserPermission(code));
        }
        return out;
    }

    public void replaceExplicitPermissionsFromCodes(Set<String> newCodes) {
        this.permissionCodes.clear();
        if (newCodes == null || newCodes.isEmpty()) return;

        for (String c : newCodes) {
            if (c == null || c.isBlank()) continue;
            TenantUserPermission vo = new TenantUserPermission(c);
            this.permissionCodes.add(vo.code());
        }
    }

    public void grantExplicitPermission(TenantUserPermission p) {
        if (p == null) return;
        this.permissionCodes.add(p.code());
    }

    public void revokeExplicitPermission(String code) {
        if (code == null) return;
        this.permissionCodes.remove(code.trim().toUpperCase(Locale.ROOT));
    }

    // ==========
    // Password reset (domínio manda)
    // ==========
    public void setPasswordReset(String token, Instant expiresAt) {
        this.passwordResetToken = token;
        this.passwordResetExpiresAt = expiresAt;
    }

    public void clearPasswordResetToken() {
        this.passwordResetToken = null;
        this.passwordResetExpiresAt = null;
    }

    // ==========
    // Compat (aliases) - para código antigo não quebrar
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
        for (String code : getPermissionCodes()) {
            out.add(new SimpleGrantedAuthority(code));
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
