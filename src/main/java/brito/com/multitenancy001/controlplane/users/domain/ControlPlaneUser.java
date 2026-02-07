package brito.com.multitenancy001.controlplane.users.domain;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.security.ControlPlanePermission;
import brito.com.multitenancy001.controlplane.security.ControlPlaneRole;
import brito.com.multitenancy001.shared.domain.EmailNormalizer;
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
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder(toBuilder = true)
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

    @Column(name = "suspended_by_account", nullable = false)
    @Builder.Default
    private boolean suspendedByAccount = false;

    @Column(name = "suspended_by_admin", nullable = false)
    @Builder.Default
    private boolean suspendedByAdmin = false;

    @Embedded
    @Builder.Default
    private AuditInfo audit = new AuditInfo();

    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "controlplane_user_permissions",
            joinColumns = @JoinColumn(name = "user_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", length = 120, nullable = false)
    @Builder.Default
    private Set<ControlPlanePermission> explicitPermissions = new LinkedHashSet<>();

    // =========================================================
    // FACTORY (criação explícita, sem @PrePersist/@PreUpdate)
    // =========================================================
    public static ControlPlaneUser createAdminUser(
            Account account,
            String name,
            String email,
            String passwordHash,
            ControlPlaneRole role,
            EntityOrigin origin
    ) {
        if (account == null) throw new IllegalArgumentException("account é obrigatório");
        if (passwordHash == null || passwordHash.isBlank()) throw new IllegalArgumentException("password hash é obrigatório");

        String normalizedEmail = EmailNormalizer.normalizeOrNull(email);
        if (normalizedEmail == null) throw new IllegalArgumentException("email inválido");

        String normalizedName = normalizeName(name);
        if (normalizedName == null) throw new IllegalArgumentException("name é obrigatório");

        return ControlPlaneUser.builder()
                .account(account)
                .name(normalizedName)
                .email(normalizedEmail)
                .password(passwordHash)
                .role(role)
                .origin(origin == null ? EntityOrigin.ADMIN : origin)
                .mustChangePassword(false)
                .deleted(false)
                .suspendedByAccount(false)
                .suspendedByAdmin(false)
                .build();
    }

    public static ControlPlaneUser createBuiltInUser(
            Account account,
            String name,
            String email,
            String passwordHash,
            ControlPlaneRole role
    ) {
        return createAdminUser(account, name, email, passwordHash, role, EntityOrigin.BUILT_IN);
    }

    private static String normalizeName(String name) {
        if (name == null) return null;
        String trimmed = name.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    @Override
    public AuditInfo getAudit() {
        return audit;
    }

    @Override
    public boolean isDeleted() {
        return deleted;
    }

    public boolean isSuspended() {
        return suspendedByAccount || suspendedByAdmin;
    }

    public boolean isEnabled() {
        return !deleted && !isSuspended();
    }

    public boolean isAccountNonLocked(Instant now) {
        if (now == null) throw new IllegalArgumentException("now is required (use AppClock.instant() in application layer)");
        return lockedUntil == null || !now.isBefore(lockedUntil);
    }

    public boolean isEnabledForLogin(Instant now) {
        return isEnabled() && isAccountNonLocked(now);
    }

    public void markLastLogin(Instant now) {
        if (now == null) throw new IllegalArgumentException("now é obrigatório");
        this.lastLoginAt = now;
    }

    public void rename(String newName) {
        String normalized = normalizeName(newName);
        if (normalized == null) throw new IllegalArgumentException("name é obrigatório");
        this.name = normalized;
    }

    public void changeEmail(String newEmail) {
        String normalized = EmailNormalizer.normalizeOrNull(newEmail);
        if (normalized == null) throw new IllegalArgumentException("email inválido");
        this.email = normalized;
    }

    public void changePasswordHash(String newPasswordHash, Instant now) {
        if (newPasswordHash == null || newPasswordHash.isBlank()) throw new IllegalArgumentException("password hash é obrigatório");
        if (now == null) throw new IllegalArgumentException("now é obrigatório");

        this.password = newPasswordHash;
        this.mustChangePassword = false;
        this.passwordChangedAt = now;

        this.passwordResetToken = null;
        this.passwordResetExpiresAt = null;
    }

    public void setTemporaryPasswordHash(String newPasswordHash) {
        if (newPasswordHash == null || newPasswordHash.isBlank()) throw new IllegalArgumentException("password hash é obrigatório");

        this.password = newPasswordHash;
        this.mustChangePassword = true;
        this.passwordChangedAt = null;

        this.passwordResetToken = null;
        this.passwordResetExpiresAt = null;
    }

    public void requirePasswordChange() {
        this.mustChangePassword = true;
        this.passwordChangedAt = null;
    }

    public void clearMustChangePassword() {
        this.mustChangePassword = false;
    }

    public void changeRole(ControlPlaneRole newRole) {
        this.role = newRole;
    }

    public void suspendByAccount() { this.suspendedByAccount = true; }
    public void unsuspendByAccount() { this.suspendedByAccount = false; }

    public void suspendByAdmin() { this.suspendedByAdmin = true; }
    public void unsuspendByAdmin() { this.suspendedByAdmin = false; }

    public void softDelete() {
        if (this.deleted) return;
        this.deleted = true;
    }

    public void restore() {
        if (!this.deleted) return;
        this.deleted = false;
        if (this.audit != null) this.audit.clearDeleted();
    }

    public void clearSecurityLockState() {
        this.lockedUntil = null;
    }

    public void clearPasswordResetToken() {
        this.passwordResetToken = null;
        this.passwordResetExpiresAt = null;
    }

    public Set<ControlPlanePermission> getExplicitPermissions() {
        return explicitPermissions == null ? Set.of() : Set.copyOf(explicitPermissions);
    }

    public Set<ControlPlanePermission> getPermissions() {
        return getExplicitPermissions();
    }

    public void grantExplicitPermission(ControlPlanePermission p) {
        PermissionScopeValidator.requireControlPlanePermission(p);
        if (this.explicitPermissions == null) this.explicitPermissions = new LinkedHashSet<>();
        this.explicitPermissions.add(p);
    }

    public void revokeExplicitPermission(ControlPlanePermission p) {
        if (this.explicitPermissions == null) return;
        this.explicitPermissions.remove(p);
    }

    public void replaceExplicitPermissions(Set<ControlPlanePermission> newPermissions) {
        if (this.explicitPermissions == null) this.explicitPermissions = new LinkedHashSet<>();
        this.explicitPermissions.clear();
        if (newPermissions == null || newPermissions.isEmpty()) return;

        for (ControlPlanePermission p : newPermissions) {
            PermissionScopeValidator.requireControlPlanePermission(p);
            this.explicitPermissions.add(p);
        }
    }
}
