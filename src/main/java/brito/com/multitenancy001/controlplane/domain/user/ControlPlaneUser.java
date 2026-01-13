package brito.com.multitenancy001.controlplane.domain.user;

import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import brito.com.multitenancy001.controlplane.domain.account.Account;
import brito.com.multitenancy001.controlplane.security.ControlPlaneRole;
import brito.com.multitenancy001.shared.security.PermissionScopeValidator;
import brito.com.multitenancy001.shared.validation.ValidationPatterns;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(
    name = "controlplane_users",
    schema = "public",
    uniqueConstraints = {
        @UniqueConstraint(name = "ux_controlplane_users_username", columnNames = {"account_id", "username"}),
        @UniqueConstraint(name = "ux_controlplane_users_email", columnNames = {"account_id", "email"})
    }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@ToString(exclude = { "account", "password" })
public class ControlPlaneUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "suspended_by_account", nullable = false)
    @Builder.Default
    private boolean suspendedByAccount = false;

    @Column(name = "suspended_by_admin", nullable = false)
    @Builder.Default
    private boolean suspendedByAdmin = false;

    // 🔐 SEGURANÇA
    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @Column(name = "failed_login_attempts", nullable = false)
    @Builder.Default
    private Integer failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "must_change_password", nullable = false)
    @Builder.Default
    private Boolean mustChangePassword = false;

    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    // ✅ ESTES CAMPOS EXISTEM NA MIGRATION
    @Column(name = "timezone", nullable=false,length = 50)
    @Builder.Default
    private String timezone = "America/Sao_Paulo";

    @Column(name = "locale", nullable=false,length = 10)
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

    // 🧾 AUDITORIA
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
    
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "controlplane_user_permissions",
        schema = "public",
        joinColumns = @JoinColumn(name = "user_id")
    )
    @Column(name = "permission", nullable = false, length = 120)
    @Builder.Default
    private Set<String> permissions = new LinkedHashSet<>();

    
    @PrePersist
    @PreUpdate
    private void normalizePermissions() {
        // garante Set não nulo
        if (permissions == null) permissions = new LinkedHashSet<>();

        // normaliza prefixo/trim e bloqueia TEN_ no controlplane
        permissions = PermissionScopeValidator.normalizeControlPlane(permissions);
    }


    

    // ✅ se lockedUntil estiver no futuro: lock
    public boolean isAccountNonLocked(LocalDateTime now) {
        return lockedUntil == null || !lockedUntil.isAfter(now);
    }

    // ✅ enabled “puro” (não deletado / não suspenso)
    public boolean isEnabledForLogin() {
        return !deleted && !suspendedByAccount && !suspendedByAdmin;
    }

    // ✅ decisão final de login (enabled + lock)
    public boolean isEnabledForLogin(LocalDateTime now) {
        return isEnabledForLogin() && isAccountNonLocked(now);
    }

    public void softDelete(LocalDateTime now, long uniqueSuffix) {
        if (deleted) return;
        deleted = true;
        deletedAt = now;

        suspendedByAccount = true;
        suspendedByAdmin = true;

        username = "deleted_" + username + "_" + uniqueSuffix;
        email = "deleted_" + email + "_" + uniqueSuffix;
    }

    public void restore() {
        this.deleted = false;
        this.deletedAt = null;
        this.suspendedByAccount = false;
        this.suspendedByAdmin = false;
    }
}
