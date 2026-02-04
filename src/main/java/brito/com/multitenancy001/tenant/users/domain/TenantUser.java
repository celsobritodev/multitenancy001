package brito.com.multitenancy001.tenant.users.domain;

import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import brito.com.multitenancy001.shared.domain.audit.AuditInfo;
import brito.com.multitenancy001.shared.domain.audit.Auditable;
import brito.com.multitenancy001.shared.domain.audit.SoftDeletable;
import brito.com.multitenancy001.shared.domain.audit.jpa.AuditEntityListener;
import brito.com.multitenancy001.shared.domain.common.EntityOrigin;
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

    @Column(nullable = false, length = 150)
    private String email;

    // ==========
    // RBAC
    // ==========
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TenantRole role;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "tenant_users_permissions",
            joinColumns = @JoinColumn(name = "user_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 80)
    @Builder.Default
    private Set<TenantUserPermission> permissions = new LinkedHashSet<>();

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
    }

    // ==========
    // DOMAIN
    // ==========
    public boolean isEnabled() {
        return !deleted && !suspendedByAccount && !suspendedByAdmin;
    }

    public void softDelete() {
        this.deleted = true;
    }

    public void restore() {
        this.deleted = false;
        if (this.audit != null) {
            this.audit.clearDeleted();
        }
    }

    public void clearSecurityLockState() {
        this.lockedUntil = null;
    }

    public void clearPasswordResetToken() {
        this.passwordResetToken = null;
        this.passwordResetExpiresAt = null;
    }

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
        // role + permissions
        LinkedHashSet<GrantedAuthority> out = new LinkedHashSet<>();
        if (role != null) out.add(new SimpleGrantedAuthority("ROLE_" + role.name()));
        if (permissions != null) {
            for (TenantUserPermission p : permissions) {
                if (p != null) out.add(new SimpleGrantedAuthority(p.name()));
            }
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
    public boolean isAccountNonLocked() {
        // sem AppClock aqui (domínio puro); quem precisar deve validar antes ou injetar via serviço
        return lockedUntil == null || lockedUntil.isBefore(Instant.now());
    }
}

