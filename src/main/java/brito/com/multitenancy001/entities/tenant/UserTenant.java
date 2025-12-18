package brito.com.multitenancy001.entities.tenant;

import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import brito.com.multitenancy001.configuration.ValidationPatterns;


import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users_tenant")  // ⚠️ NOME DIFERENTE, schema DINÂMICO
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@ToString(exclude = "password")
public class UserTenant {

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
    private UserTenantRole role;

    // ✅ APENAS ID (sem FK cross-schema)
    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @ElementCollection
    @CollectionTable(
        name = "user_tenant_permissions",  // ⚠️ TABELA SEPARADA
        joinColumns = @JoinColumn(name = "user_tenant_id")
    )
    @Column(name = "permission", length = 100)
    @Builder.Default
    private List<String> permissions = new ArrayList<>();

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

    // ✅ CAMPOS EXTRAS para clientes
    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    // 🌎 CONFIGURAÇÕES
    @Column(name = "timezone", length = 50)
    @Builder.Default
    private String timezone = "America/Sao_Paulo";

    @Column(name = "locale", length = 10)
    @Builder.Default
    private String locale = "pt_BR";

    // 🧾 AUDITORIA COMPLETA
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

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "password_reset_token")
    private String passwordResetToken;

    @Column(name = "password_reset_expires")
    private LocalDateTime passwordResetExpires;

    @PrePersist
    protected void onCreate() {
        if (this.username != null) {
            this.username = this.username.toLowerCase().trim();
        }
        addDefaultPermissions();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    private void addDefaultPermissions() {
        if (this.permissions == null) {
            this.permissions = new ArrayList<>();
        }

        if (this.permissions.isEmpty()) {
            // Permissões específicas para TENANT
          switch (this.role) {
    case TENANT_ADMIN -> permissions.addAll(List.of(
        "USER_CREATE", "USER_UPDATE", "USER_DELETE", "USER_VIEW",
        "PRODUCT_CREATE", "PRODUCT_UPDATE", "PRODUCT_DELETE", "PRODUCT_VIEW",
        "SALE_CREATE", "SALE_UPDATE", "SALE_DELETE", "SALE_VIEW",
        "REPORT_VIEW", "SETTINGS_MANAGE"
    ));
    case MANAGER -> permissions.addAll(List.of(
        "PRODUCT_CREATE", "PRODUCT_UPDATE", "PRODUCT_VIEW",
        "SALE_CREATE", "SALE_VIEW", "REPORT_VIEW"
    ));
    case USER -> permissions.add("VIEW_BASIC");
}
        }
    }

    /**
     * MÉTODOS ESPECÍFICOS PARA TENANT (copiados do User original)
     */
    public boolean isAccountNonLocked() {
        if (lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now())) {
            return false;
        }
        return active && !deleted;
    }

    public void incrementFailedLoginAttempts() {
        this.failedLoginAttempts++;
        if (this.failedLoginAttempts >= 5) {
            this.lockedUntil = LocalDateTime.now().plusMinutes(30);
        }
    }

    public void resetFailedLoginAttempts() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
        this.lastLogin = LocalDateTime.now();
    }

    public boolean hasPermission(String permission) {
        return this.permissions.contains(permission) || 
               this.permissions.contains("ALL") || 
               this.role.isAdmin();
    }

    public void addPermission(String permission) {
        if (!this.permissions.contains(permission)) {
            this.permissions.add(permission);
        }
    }

    public void removePermission(String permission) {
        this.permissions.remove(permission);
    }

    public void softDelete() {
        this.deleted = true;
        this.deletedAt = LocalDateTime.now();
        this.active = false;
        var timestamp = System.currentTimeMillis();
        this.username = "deleted_" + this.username + "_" + timestamp;
        this.email = "deleted_" + this.email + "_" + timestamp;
    }

    public void restore() {
        this.deleted = false;
        this.deletedAt = null;
        this.active = true;

        if (this.username.startsWith("deleted_")) {
            String original = this.username.substring("deleted_".length());
            original = original.replaceFirst("_[0-9]+$", "");
            this.username = original;
        }

        if (this.email.startsWith("deleted_")) {
            String original = this.email.substring("deleted_".length());
            original = original.replaceFirst("_[0-9]+$", "");
            this.email = original;
        }
    }

    public void changePassword(String newPassword) {
        this.password = newPassword;
        this.passwordChangedAt = LocalDateTime.now();
        this.mustChangePassword = false;
    }

    public boolean isPasswordExpired() {
        if (this.passwordChangedAt == null) return true;
        return this.passwordChangedAt.plusDays(90).isBefore(LocalDateTime.now());
    }
}