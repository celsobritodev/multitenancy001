package brito.com.multitenancy001.controlplane.users.domain;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.security.ControlPlanePermission;
import brito.com.multitenancy001.controlplane.security.ControlPlaneRole;
import brito.com.multitenancy001.shared.domain.audit.AuditInfo;
import brito.com.multitenancy001.shared.domain.audit.Auditable;
import brito.com.multitenancy001.shared.domain.audit.SoftDeletable;
import brito.com.multitenancy001.shared.domain.audit.jpa.AuditEntityListener;
import brito.com.multitenancy001.shared.domain.common.EntityOrigin;
import brito.com.multitenancy001.shared.security.PermissionScopeValidator;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "controlplane_users")
@EntityListeners(AuditEntityListener.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"account", "password", "explicitPermissions"})
public class ControlPlaneUser implements Auditable, SoftDeletable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(name = "user_origin", nullable = false, length = 20)
    @Builder.Default
    private EntityOrigin origin = EntityOrigin.ADMIN;

    public boolean isBuiltInUser() {
        return this.origin == EntityOrigin.BUILT_IN;
    }

    @Setter(AccessLevel.NONE)
    @Column(nullable = false, length = 100)
    private String name;

    @Setter(AccessLevel.NONE)
    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Setter(AccessLevel.NONE)
    @Column(name = "email", nullable = false, columnDefinition = "citext")
    private String email;


    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 50)
    private ControlPlaneRole role;

    // ==========
    // AUTH / SECURITY (instantes reais => Instant)
    // ==========
    @Column(name = "must_change_password", nullable = false)
    @Builder.Default
    private boolean mustChangePassword = false;

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

    // ==========
    // AUDIT (fonte única)
    // ==========
    @Embedded
    @Builder.Default
    private AuditInfo audit = new AuditInfo();

    // ==========
    // SOFT DELETE
    // ==========
    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    // ==========
    // Permissões explícitas (override)
    // ==========
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "controlplane_user_permissions",
            joinColumns = @JoinColumn(name = "user_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", length = 120, nullable = false)
    @Builder.Default
    private Set<ControlPlanePermission> explicitPermissions = new LinkedHashSet<>();

    // ==========
    // Contracts (Auditable / SoftDeletable)
    // ==========
    @Override
    public AuditInfo getAudit() {
        return audit;
    }

    @Override
    public boolean isDeleted() {
        return deleted;
    }

    // ==========
    // Rules (status)
    // ==========
    public boolean isSuspended() {
        return suspendedByAccount || suspendedByAdmin;
    }

    public boolean isEnabled() {
        return !deleted && !isSuspended();
    }

    public boolean isAccountNonLocked(Instant now) {
        if (now == null) now = Instant.now();
        return lockedUntil == null || !now.isBefore(lockedUntil);
    }

    public boolean isEnabledForLogin(Instant now) {
        return isEnabled() && isAccountNonLocked(now);
    }

    // ==========
    // Mutations
    // ==========
    public void rename(String newName) {
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("name é obrigatório");
        }
        this.name = newName.trim();
    }

    public void changePasswordHash(String newPasswordHash) {
        if (newPasswordHash == null || newPasswordHash.isBlank()) {
            throw new IllegalArgumentException("password hash é obrigatório");
        }
        this.password = newPasswordHash;
    }

    public void requirePasswordChange() {
        this.mustChangePassword = true;
    }

    public void clearMustChangePassword() {
        this.mustChangePassword = false;
    }

    public void changeRole(ControlPlaneRole newRole) {
        this.role = newRole;
    }

    public void suspendByAccount() {
        this.suspendedByAccount = true;
    }

    public void unsuspendByAccount() {
        this.suspendedByAccount = false;
    }

    public void suspendByAdmin() {
        this.suspendedByAdmin = true;
    }

    public void unsuspendByAdmin() {
        this.suspendedByAdmin = false;
    }

    public void softDelete() {
        if (this.deleted) return;
        this.deleted = true;
    }

    public void restore() {
        if (!this.deleted) return;
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

    // ==========
    // Explicit permissions API (domínio manda)
    // ==========
    public Set<ControlPlanePermission> getExplicitPermissions() {
        return Set.copyOf(explicitPermissions);
    }

    /**
     * Alias de compat para infra legada (AuthoritiesFactory usa getPermissions()).
     */
    public Set<ControlPlanePermission> getPermissions() {
        return getExplicitPermissions();
    }

    public void grantExplicitPermission(ControlPlanePermission p) {
        PermissionScopeValidator.requireControlPlanePermission(p);
        this.explicitPermissions.add(p);
    }

    public void revokeExplicitPermission(ControlPlanePermission p) {
        this.explicitPermissions.remove(p);
    }

    public void replaceExplicitPermissions(Set<ControlPlanePermission> newPermissions) {
        this.explicitPermissions.clear();
        if (newPermissions == null || newPermissions.isEmpty()) return;

        for (ControlPlanePermission p : newPermissions) {
            PermissionScopeValidator.requireControlPlanePermission(p);
            this.explicitPermissions.add(p);
        }
    }
}
