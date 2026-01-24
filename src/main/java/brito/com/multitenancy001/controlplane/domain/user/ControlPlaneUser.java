package brito.com.multitenancy001.controlplane.domain.user;

import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import brito.com.multitenancy001.controlplane.domain.account.Account;
import brito.com.multitenancy001.controlplane.security.ControlPlanePermission;
import brito.com.multitenancy001.controlplane.security.ControlPlaneRole;
import brito.com.multitenancy001.shared.db.Schemas;
import brito.com.multitenancy001.shared.domain.audit.AuditInfo;
import brito.com.multitenancy001.shared.domain.audit.Auditable;
import brito.com.multitenancy001.shared.domain.audit.SoftDeletable;
import brito.com.multitenancy001.shared.infrastructure.audit.AuditEntityListener;
import brito.com.multitenancy001.shared.security.PermissionScopeValidator;
import brito.com.multitenancy001.shared.validation.ValidationPatterns;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "controlplane_users")
@EntityListeners(AuditEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@ToString(exclude = { "account", "password" })
public class ControlPlaneUser implements Auditable, SoftDeletable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_origin", nullable = false, length = 20)
    @Builder.Default
    private ControlPlaneUserOrigin origin = ControlPlaneUserOrigin.ADMIN;

    public boolean isBuiltInUser() { return this.origin == ControlPlaneUserOrigin.BUILT_IN; }

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "username", nullable = false, length = 100)
    @Pattern(regexp = ValidationPatterns.USERNAME_PATTERN, message = "Username inválido.")
    private String username;

    @Column(name = "password", nullable = false, length = 255)
    private String password;

    @Column(name = "email", nullable = false, length = 150)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 50)
    private ControlPlaneRole role;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "suspended_by_account", nullable = false)
    @Builder.Default
    private boolean suspendedByAccount = false;

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

    @Column(name = "must_change_password", nullable = false)
    @Builder.Default
    private boolean mustChangePassword = false;

    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    @Column(name = "timezone", nullable=false,length = 60)
    @Builder.Default
    private String timezone = "America/Sao_Paulo";

    @Column(name = "locale", nullable=false,length = 20)
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

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

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

    @PrePersist
    @PreUpdate
    private void normalizePermissions() {
        if (permissions == null) {
            permissions = new LinkedHashSet<>();
            return;
        }

        // normaliza sem reatribuir a collection gerenciada
        var normalized = PermissionScopeValidator.normalizeControlPlanePermissions(permissions);
        permissions.clear();
        permissions.addAll(normalized);
    }


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
        if (isBuiltInUser()) throw new IllegalStateException("SYSTEM_USER_READONLY");
        if (deleted) return;

        deleted = true;
        deletedAt = now;
    }

    public void restore() {
        this.deleted = false;
        this.deletedAt = null;
        this.suspendedByAccount = false;
        this.suspendedByAdmin = false;
    }
    
 // ============================================
 // State semantics (PADRÃO ÚNICO)
 // ============================================

 /**
  * enabled = usuário operacionalmente apto:
  * - não deletado
  * - não suspenso pela conta
  * - não suspenso pelo admin
  */
 // ============================================
 // State semantics (PADRÃO ÚNICO)
 // ============================================

 /**
  * enabled = usuário operacionalmente apto:
  * - não deletado (soft-delete)
  * - não suspenso pela conta
  * - não suspenso pelo admin
  */
 public boolean isEnabled() {
     return !deleted && !suspendedByAccount && !suspendedByAdmin;
 }

 /** notDeleted = apenas soft-delete */
 public boolean isNotDeleted() {
     return !deleted;
 }

 /** suspended = qualquer motivo */
 public boolean isSuspended() {
     return suspendedByAccount || suspendedByAdmin;
 }


}
