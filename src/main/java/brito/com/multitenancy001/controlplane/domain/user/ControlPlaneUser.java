package brito.com.multitenancy001.controlplane.domain.user;

import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import brito.com.multitenancy001.controlplane.domain.account.Account;
import brito.com.multitenancy001.shared.validation.ValidationPatterns;

import java.time.LocalDateTime;

@Entity
@Table(name = "users_account", schema = "public")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@ToString(exclude = { "account", "password" })
public class ControlPlaneUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 100)
    @Pattern(regexp = ValidationPatterns.USERNAME_PATTERN, message = "Username inválido.")
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 150)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ControlPlaneRole role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "suspended_by_account", nullable = false)
    @Builder.Default
    private boolean suspendedByAccount = false;

    // ✅ NOVO
    @Column(name = "suspended_by_admin", nullable = false)
    @Builder.Default
    private boolean suspendedByAdmin = false;

    // 🔐 SEGURANÇA
    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    @Column(name = "failed_login_attempts")
    @Builder.Default
    private Integer failedLoginAttempts = 0;

    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "must_change_password")
    @Builder.Default
    private Boolean mustChangePassword = false;

    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    // 🧾 AUDITORIA
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted")
    @Builder.Default
    private boolean deleted = false;

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // ✅ regra única para login
    public boolean isEnabledForLogin() {
        if (lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now())) return false;
        return !deleted && !suspendedByAccount && !suspendedByAdmin;
    }

    // se você usa esse método em algum lugar, deixe coerente:
    public boolean isAccountNonLocked() {
        return isEnabledForLogin();
    }

    public void softDelete() {
        this.deleted = true;
        this.deletedAt = LocalDateTime.now();
        // deletado sempre bloqueia login
        this.suspendedByAccount = true;
        this.suspendedByAdmin = true;

        var timestamp = System.currentTimeMillis();
        this.username = "deleted_" + this.username + "_" + timestamp;
        this.email = "deleted_" + this.email + "_" + timestamp;
    }

    public void restore() {
        this.deleted = false;
        this.deletedAt = null;

        // restaurar não “des-suspende” admin por padrão — mas como é plataforma, você decide.
        this.suspendedByAccount = false;
        this.suspendedByAdmin = false;
    }
}
