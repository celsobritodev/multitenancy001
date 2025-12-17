package brito.com.multitenancy001.entities.account;

import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import brito.com.multitenancy001.configuration.ValidationPatterns;
import java.time.LocalDateTime;

@Entity
@Table(name = "users_account", schema = "public")  // ⚠️ NOME DIFERENTE
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@ToString(exclude = { "account", "password" })
public class UserAccount {

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
    private UserRole role;

    // ✅ FK COMPLETA no ACCOUNT
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

	/*
	 * @ElementCollection
	 * 
	 * @CollectionTable( name = "user_account_permissions", // ⚠️ TABELA SEPARADA
	 * schema = "public", joinColumns = @JoinColumn(name = "user_account_id"),
	 * uniqueConstraints = @UniqueConstraint(columnNames = {"user_account_id",
	 * "permission"}) )
	 * 
	 * @Column(name = "permission", length = 100)
	 * 
	 * @Builder.Default private List<String> permissions = new ArrayList<>();
	 */

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

    // ⚠️ CAMPOS SIMPLES - admin não precisa de avatar/phone
    // SEM avatar_url, phone, created_by, updated_by

    // 🌎 CONFIGURAÇÕES
    @Column(name = "timezone", length = 50)
    @Builder.Default
    private String timezone = "America/Sao_Paulo";

    @Column(name = "locale", length = 10)
    @Builder.Default
    private String locale = "pt_BR";

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

    @Column(name = "password_reset_token")
    private String passwordResetToken;

    @Column(name = "password_reset_expires")
    private LocalDateTime passwordResetExpires;

   

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

   

    /**
     * MÉTODOS DA ENTIDADE ORIGINAL (adaptados)
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