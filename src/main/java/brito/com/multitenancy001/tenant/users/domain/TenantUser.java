package brito.com.multitenancy001.tenant.users.domain;

import brito.com.multitenancy001.shared.domain.audit.AuditInfo;
import brito.com.multitenancy001.shared.domain.audit.Auditable;
import brito.com.multitenancy001.shared.domain.audit.SoftDeletable;
import brito.com.multitenancy001.shared.domain.audit.jpa.AuditEntityListener;
import brito.com.multitenancy001.shared.domain.common.EntityOrigin;
import brito.com.multitenancy001.shared.security.PermissionScopeValidator;
import brito.com.multitenancy001.shared.validation.ValidationPatterns;
import brito.com.multitenancy001.tenant.security.TenantPermission;
import brito.com.multitenancy001.tenant.security.TenantRole;
import brito.com.multitenancy001.tenant.security.TenantRolePermissions;
import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@DynamicUpdate
@Entity
@Table(
    name = "tenant_users",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_tenant_users_email_account", columnNames = {"email", "account_id"})
    }
)
@EntityListeners(AuditEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "password")
public class TenantUser implements Auditable, SoftDeletable {

    private static final int EMAIL_MAX_LEN = 150;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_origin", nullable = false, length = 20)
    @Builder.Default
    private EntityOrigin  origin = EntityOrigin.ADMIN;

    public boolean isBuiltInUser() {
        return this.origin == EntityOrigin.BUILT_IN;
    }

    @Column(name = "password_reset_token", length = 255)
    private String passwordResetToken;

    @Column(name = "password_reset_expires")
    private LocalDateTime passwordResetExpires;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = EMAIL_MAX_LEN)
    @Pattern(regexp = ValidationPatterns.EMAIL_PATTERN, message = "Email inválido.")
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TenantRole role;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "suspended_by_account", nullable = false)
    @Builder.Default
    private boolean suspendedByAccount = false;

    @Column(name = "suspended_by_admin", nullable = false)
    @Builder.Default
    private boolean suspendedByAdmin = false;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "tenant_user_permissions",
            joinColumns = @JoinColumn(name = "tenant_user_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 120)
    @Builder.Default
    private Set<TenantPermission> permissions = new LinkedHashSet<>();

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @Column(name = "failed_login_attempts", nullable = false)
    @Builder.Default
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "must_change_password", nullable = false)
    @Builder.Default
    private boolean mustChangePassword = false;

    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "timezone", nullable = false, length = 60)
    @Builder.Default
    private String timezone = "America/Sao_Paulo";

    @Column(name = "locale", nullable = false, length = 20)
    @Builder.Default
    private String locale = "pt_BR";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        if (origin == null) origin = EntityOrigin.ADMIN;
        if (role == null) throw new IllegalStateException("Role is required");

        if (permissions == null) {
            permissions = new LinkedHashSet<>();
        }

        if (email != null) email = email.toLowerCase().trim();

        // ✅ garante permissões do role (sem duplicar, pois é Set)
        permissions.addAll(TenantRolePermissions.permissionsFor(role));

        var normalized = PermissionScopeValidator.normalizeTenantPermissions(permissions);

        if (!permissions.equals(normalized)) {
            permissions.clear();
            permissions.addAll(normalized);
        }
    }

    @Embedded
    @Builder.Default
    private AuditInfo audit = new AuditInfo();

    @Override
    public AuditInfo getAudit() { return audit; }

    @Override
    public boolean isDeleted() { return deleted; }

    public boolean isAccountNonLocked(LocalDateTime now) {
        return lockedUntil == null || !lockedUntil.isAfter(now);
    }

    public boolean isEnabledForLogin() {
        return isEnabled();
    }

    public boolean isEnabledForLogin(LocalDateTime now) {
        return isEnabledForLogin() && isAccountNonLocked(now);
    }

    public void softDelete(LocalDateTime now, long suffixEpochMillis) {
        if (deleted) return;
        if (now == null) throw new IllegalArgumentException("now is required");

        deleted = true;
        deletedAt = now;

        suspendedByAccount = true;
        suspendedByAdmin = true;

        // liberar unique(email, account_id)
        String ts = String.valueOf(suffixEpochMillis);
        String newEmail = "deleted_" + (email == null ? "user" : email) + "_" + ts;
        if (newEmail.length() > EMAIL_MAX_LEN) newEmail = newEmail.substring(0, EMAIL_MAX_LEN);
        email = newEmail;
    }

    public void restore() {
        if (!deleted) return;

        deleted = false;
        deletedAt = null;
        suspendedByAdmin = false;
        // não altera suspendedByAccount aqui
    }

    public boolean isEnabled() {
        return !deleted && !suspendedByAccount && !suspendedByAdmin;
    }

    public boolean isNotDeleted() {
        return !deleted;
    }

    public boolean isSuspended() {
        return suspendedByAccount || suspendedByAdmin;
    }
}
