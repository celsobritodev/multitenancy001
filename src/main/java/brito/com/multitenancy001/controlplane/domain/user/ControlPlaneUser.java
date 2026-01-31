package brito.com.multitenancy001.controlplane.domain.user;

import brito.com.multitenancy001.controlplane.domain.account.Account;
import brito.com.multitenancy001.controlplane.security.ControlPlanePermission;
import brito.com.multitenancy001.controlplane.security.ControlPlaneRole;
import brito.com.multitenancy001.shared.db.Schemas;
import brito.com.multitenancy001.shared.domain.audit.AuditInfo;
import brito.com.multitenancy001.shared.domain.audit.Auditable;
import brito.com.multitenancy001.shared.domain.audit.SoftDeletable;
import brito.com.multitenancy001.shared.domain.common.EntityOrigin;
import brito.com.multitenancy001.shared.persistence.audit.AuditEntityListener;
import brito.com.multitenancy001.shared.security.PermissionScopeValidator;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "controlplane_users")
@EntityListeners(AuditEntityListener.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"account", "password"})
public class ControlPlaneUser implements Auditable, SoftDeletable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(name = "user_origin", nullable = false, length = 20)
    @Builder.Default
    private EntityOrigin  origin = EntityOrigin .ADMIN;

    public boolean isBuiltInUser() { return this.origin == EntityOrigin .BUILT_IN; }

    @Setter(AccessLevel.NONE)
    @Column(nullable = false, length = 100)
    private String name;

    @Setter(AccessLevel.NONE)
    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Setter(AccessLevel.NONE)
    @Column(name = "email", nullable = false, length = 150)
    private String email;

    @Setter(AccessLevel.NONE)
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 50)
    private ControlPlaneRole role;

    @Setter(AccessLevel.NONE)
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Setter(AccessLevel.NONE)
    @Column(name = "suspended_by_account", nullable = false)
    @Builder.Default
    private boolean suspendedByAccount = false;

    @Setter(AccessLevel.NONE)
    @Column(name = "suspended_by_admin", nullable = false)
    @Builder.Default
    private boolean suspendedByAdmin = false;

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @Column(name = "failed_login_attempts", nullable = false)
    @Builder.Default
    private int failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Setter(AccessLevel.NONE)
    @Column(name = "must_change_password", nullable = false)
    @Builder.Default
    private boolean mustChangePassword = false;

    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    @Column(name = "timezone", nullable = false, length = 60)
    @Builder.Default
    private String timezone = "America/Sao_Paulo";

    @Column(name = "locale", nullable = false, length = 20)
    @Builder.Default
    private String locale = "pt_BR";

    @Column(name = "password_reset_token", length = 255)
    private String passwordResetToken;

    @Column(name = "password_reset_expires")
    private LocalDateTime passwordResetExpires;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Setter(AccessLevel.NONE)
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Setter(AccessLevel.NONE)
    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    // ===== AUDIT (ator)
    @Embedded
    @Builder.Default
    private AuditInfo audit = new AuditInfo();

    @Override
    public AuditInfo getAudit() { return audit; }

    @Override
    public boolean isDeleted() { return deleted; }

    @Setter(AccessLevel.NONE)
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "controlplane_user_permissions",
            schema = Schemas.CONTROL_PLANE,
            joinColumns = @JoinColumn(name = "user_id")
    )
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 120)
    @Builder.Default
    private Set<ControlPlanePermission> permissions = new LinkedHashSet<>();

    // =========================================================
    // RESERVED USERS (4 administrativos)
    // =========================================================

    public boolean isReservedBuiltInAdmin() {
        var base = (_originalEmail != null) ? _originalEmail : this.email;
        return ControlPlaneBuiltInUsers.isReservedEmail(base);
    }

    private void assertMutable(String action) {
        if (isReservedBuiltInAdmin()) {
            throw new IllegalStateException("RESERVED_BUILTIN_USER_READONLY: " + action);
        }
    }

    // setters controlados (bloqueados quando reservado)

    public void setOrigin(EntityOrigin  origin) {
        assertMutable("SET_ORIGIN");
        this.origin = origin;
    }

    public void setName(String name) {
        assertMutable("SET_NAME");
        this.name = name;
    }

    public void setEmail(String email) {
        assertMutable("SET_EMAIL");
        this.email = email;
    }

    public void setRole(ControlPlaneRole role) {
        assertMutable("SET_ROLE");
        this.role = role;
    }

    public void setAccount(Account account) {
        assertMutable("SET_ACCOUNT");
        this.account = account;
    }

    public void setSuspendedByAccount(boolean suspendedByAccount) {
        assertMutable("SET_SUSPENDED_BY_ACCOUNT");
        this.suspendedByAccount = suspendedByAccount;
    }

    public void setSuspendedByAdmin(boolean suspendedByAdmin) {
        assertMutable("SET_SUSPENDED_BY_ADMIN");
        this.suspendedByAdmin = suspendedByAdmin;
    }

    public void setMustChangePassword(boolean mustChangePassword) {
        // ✅ permitido até para reservados (só senha pode mudar; esse flag faz parte da gestão de senha)
        this.mustChangePassword = mustChangePassword;
    }

    public void setPermissions(Set<ControlPlanePermission> permissions) {
        assertMutable("SET_PERMISSIONS");
        this.permissions = (permissions == null) ? new LinkedHashSet<>() : new LinkedHashSet<>(permissions);
    }

    // ✅ setters “de senha” são permitidos para reservados
    public void setPassword(String password) {
        this.password = password;
    }

    public void setPasswordChangedAt(LocalDateTime passwordChangedAt) {
        this.passwordChangedAt = passwordChangedAt;
    }

    // =========================================================
    // Normalizações (JPA: apenas 1 callback por tipo)
    // =========================================================

    @PrePersist
    private void prePersist() {
        normalizeInternal();
        normalizePermissionsInternal();
    }

    @PreUpdate
    private void preUpdate() {
        normalizeInternal();
        normalizePermissionsInternal();
        preventReservedMutationBySnapshotInternal();
    }

    private void normalizeInternal() {
        if (email != null) email = email.trim().toLowerCase();
    }

    private void normalizePermissionsInternal() {
        if (permissions == null) {
            permissions = new LinkedHashSet<>();
            return;
        }
        var normalized = PermissionScopeValidator.normalizeControlPlanePermissions(permissions);
        permissions.clear();
        permissions.addAll(normalized);
    }

    // =========================================================
    // Snapshot + validação em @PreUpdate (rede de segurança)
    // =========================================================

    private void preventReservedMutationBySnapshotInternal() {
        // decide se é reservado usando snapshot ou fallback
        var base = (_originalEmail != null) ? _originalEmail : this.email;
        if (!ControlPlaneBuiltInUsers.isReservedEmail(base)) return;

        // 🔒 se não existe snapshot ainda, não valida por snapshot
        if (_originalEmail == null) return;

        // para reservado: email/nome/origin/role/suspensões/deleted NÃO podem mudar
        if (!safeEq(base, this.email)) throw new IllegalStateException("RESERVED_BUILTIN_USER_READONLY: EMAIL");
        if (!safeEq(_originalName, this.name)) throw new IllegalStateException("RESERVED_BUILTIN_USER_READONLY: NAME");
        if (_originalRole != this.role) throw new IllegalStateException("RESERVED_BUILTIN_USER_READONLY: ROLE");
        if (_originalOrigin != this.origin) throw new IllegalStateException("RESERVED_BUILTIN_USER_READONLY: ORIGIN");
        if (_originalSuspendedByAccount != this.suspendedByAccount) throw new IllegalStateException("RESERVED_BUILTIN_USER_READONLY: SUSPENDED_BY_ACCOUNT");
        if (_originalSuspendedByAdmin != this.suspendedByAdmin) throw new IllegalStateException("RESERVED_BUILTIN_USER_READONLY: SUSPENDED_BY_ADMIN");
        if (_originalDeleted != this.deleted) throw new IllegalStateException("RESERVED_BUILTIN_USER_READONLY: DELETED");

        // permissions (bloqueia até mutação interna do Set)
        var original = (_originalPermissions == null) ? new LinkedHashSet<ControlPlanePermission>() : _originalPermissions;
        var current = (this.permissions == null) ? new LinkedHashSet<ControlPlanePermission>() : this.permissions;

        if (!original.equals(current)) {
            throw new IllegalStateException("RESERVED_BUILTIN_USER_READONLY: PERMISSIONS");
        }
    }

    // =========================================================
    // Snapshot (captura estado original)
    // =========================================================

    @Transient private String _originalEmail;
    @Transient private String _originalName;
    @Transient private ControlPlaneRole _originalRole;
    @Transient private EntityOrigin  _originalOrigin;
    @Transient private boolean _originalSuspendedByAccount;
    @Transient private boolean _originalSuspendedByAdmin;
    @Transient private boolean _originalDeleted;
    @Transient private Set<ControlPlanePermission> _originalPermissions;

    @PostLoad
    @PostPersist
    @PostUpdate
    private void captureOriginalState() {
        this._originalEmail = this.email;
        this._originalName = this.name;
        this._originalRole = this.role;
        this._originalOrigin = this.origin;
        this._originalSuspendedByAccount = this.suspendedByAccount;
        this._originalSuspendedByAdmin = this.suspendedByAdmin;
        this._originalDeleted = this.deleted;

        this._originalPermissions = (this.permissions == null)
                ? new LinkedHashSet<>()
                : new LinkedHashSet<>(this.permissions);
    }

    private static boolean safeEq(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    // =========================================================
    // Semânticas de estado / domínio
    // =========================================================

    public boolean isAccountNonLocked(LocalDateTime now) {
        return lockedUntil == null || !lockedUntil.isAfter(now);
    }

    public boolean isEnabledForLogin() {
        return isEnabled();
    }

    public boolean isEnabledForLogin(LocalDateTime now) {
        return isEnabledForLogin() && isAccountNonLocked(now);
    }

    public void softDelete(LocalDateTime now) {
        // ✅ trava também os reservados (mesmo que alguém mude origin)
        if (isReservedBuiltInAdmin()) throw new IllegalStateException("RESERVED_BUILTIN_USER_READONLY");
        if (isBuiltInUser()) throw new IllegalStateException("SYSTEM_USER_READONLY");
        if (deleted) return;

        deleted = true;
        deletedAt = now;
    }

    public void restore() {
        // restore é uma “ação administrativa”; o service já bloqueia reserved.
        // aqui não bloqueio para não travar cenários de recuperação de dados manual.
        this.deleted = false;
        this.deletedAt = null;
        this.suspendedByAccount = false;
        this.suspendedByAdmin = false;
    }

    /**
     * enabled = usuário operacionalmente apto:
     * - não deletado
     * - não suspenso pela conta
     * - não suspenso pelo admin
     */
    public boolean isEnabled() {
        return !deleted && !suspendedByAccount && !suspendedByAdmin;
    }

    public boolean isNotDeleted() {
        return !deleted;
    }

    public boolean isSuspended() {
        return suspendedByAccount || suspendedByAdmin;
    }

    // =========================================================
    // Security helpers (login / lock / reset) - permitido inclusive para reservados
    // =========================================================

    /**
     * Zera contadores e desbloqueia o usuário.
     * Permitido inclusive para usuários administrativos reservados.
     */
    public void clearSecurityLockState() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
    }

    /**
     * Limpa token de reset de senha.
     * Permitido inclusive para usuários administrativos reservados.
     */
    public void clearPasswordResetToken() {
        this.passwordResetToken = null;
        this.passwordResetExpires = null;
    }
}
