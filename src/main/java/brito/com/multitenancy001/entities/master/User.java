package brito.com.multitenancy001.entities.master;




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
@Table(name = "users", 
       uniqueConstraints = {
    		   @UniqueConstraint(columnNames = {"username", "account_id"}),
    		   @UniqueConstraint(columnNames = {"email", "account_id"})
       })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"account", "password"})
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 100)
    private String name;
    
    @Column(nullable = false, length = 100)
    @Pattern(regexp = ValidationPatterns.USERNAME_PATTERN, message = "Username inválido.")
    private String username;
    
    @Column(nullable = false)
    //@Pattern(regexp = ValidationPatterns.PASSWORD_PATTERN, message = "Senha fraca.")
    private String password;
    
    @Column(nullable = false, length = 150)
    private String email;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private UserRole role;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;
    
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
    
    @ElementCollection
    @CollectionTable(
        name = "user_permissions",
        joinColumns = @JoinColumn(name = "user_id"),
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "permission"})
    )
    @Column(name = "permission", length = 100)
    @Builder.Default
    private List<String> permissions = new ArrayList<>();
    
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
    
    @Column(name = "phone", length = 20)
    private String phone;
    
    @Column(name = "avatar_url")
    private String avatarUrl;
    
    @Column(name = "timezone", length = 50)
    @Builder.Default
    private String timezone = "America/Sao_Paulo";
    
    @Column(name = "locale", length = 10)
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

        // Normaliza username somente se existir
        if (this.username != null) {
            this.username = this.username.toLowerCase().trim();
        }

       
        // Adiciona permissões padrão
        addDefaultPermissions();
    }

    
    
    
    
    
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
    
    
    /**
     * Adiciona permissões padrão baseado no role do usuário
     */
    private void addDefaultPermissions() {
        if (this.permissions == null) {
            this.permissions = new ArrayList<>();
        }
        
        // Limpa permissões existentes se estiver vazio
        if (this.permissions.isEmpty()) {
            switch (this.role) {
                case ADMIN:
                    this.permissions.addAll(List.of(
                        "ACCOUNT_MANAGE",
                        "USER_CREATE",
                        "USER_UPDATE",
                        "USER_DELETE",
                        "USER_VIEW",
                        "PRODUCT_CREATE",
                        "PRODUCT_UPDATE",
                        "PRODUCT_DELETE",
                        "PRODUCT_VIEW",
                        "SALE_CREATE",
                        "SALE_UPDATE",
                        "SALE_DELETE",
                        "SALE_VIEW",
                        "SUPPLIER_CREATE",
                        "SUPPLIER_UPDATE",
                        "SUPPLIER_DELETE",
                        "SUPPLIER_VIEW",
                        "REPORT_VIEW",
                        "SETTINGS_MANAGE"
                    ));
                    break;
                    
                case PRODUCT_MANAGER:
                    this.permissions.addAll(List.of(
                        "PRODUCT_CREATE",
                        "PRODUCT_UPDATE",
                        "PRODUCT_DELETE",
                        "PRODUCT_VIEW",
                        "SUPPLIER_VIEW",
                        "REPORT_VIEW"
                    ));
                    break;
                    
                case SALES_MANAGER:
                    this.permissions.addAll(List.of(
                        "SALE_CREATE",
                        "SALE_UPDATE",
                        "SALE_VIEW",
                        "PRODUCT_VIEW",
                        "REPORT_VIEW"
                    ));
                    break;
                    
                case VIEWER:
                    this.permissions.addAll(List.of(
                        "PRODUCT_VIEW",
                        "SALE_VIEW",
                        "REPORT_VIEW"
                    ));
                    break;
                    
                case SUPPORT:
                    this.permissions.addAll(List.of(
                        "USER_VIEW",
                        "PRODUCT_VIEW",
                        "SALE_VIEW",
                        "SUPPLIER_VIEW"
                    ));
                    break;
                    
                case FINANCEIRO:
                    this.permissions.addAll(List.of(
                        "SALE_VIEW",
                        "REPORT_VIEW",
                        "FINANCE_VIEW"
                    ));
                    break;
                    
                case OPERACOES:
                    this.permissions.addAll(List.of(
                        "PRODUCT_VIEW",
                        "SALE_VIEW",
                        "SUPPLIER_VIEW"
                    ));
                    break;
            }
        }
    }
    
    /**
     * Verifica se o usuário está ativo e não bloqueado
     */
    public boolean isAccountNonLocked() {
        if (lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now())) {
            return false;
        }
        return active && !deleted;
    }
    
    /**
     * Incrementa tentativas de login falhas
     */
    public void incrementFailedLoginAttempts() {
        this.failedLoginAttempts++;
        
        // Bloqueia por 30 minutos após 5 tentativas falhas
        if (this.failedLoginAttempts >= 5) {
            this.lockedUntil = LocalDateTime.now().plusMinutes(30);
        }
    }
    
    /**
     * Reseta as tentativas de login falhas
     */
    public void resetFailedLoginAttempts() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
        this.lastLogin = LocalDateTime.now();
    }
    
    /**
     * Verifica se o usuário tem uma permissão específica
     */
    public boolean hasPermission(String permission) {
        return this.permissions.contains(permission) || 
               this.permissions.contains("ALL") ||
               this.role.isAdmin();  // ✅ Usando método do enum
    }
    
    /**
     * Adiciona uma permissão ao usuário
     */
    public void addPermission(String permission) {
        if (!this.permissions.contains(permission)) {
            this.permissions.add(permission);
        }
    }
    
    /**
     * Remove uma permissão do usuário
     */
    public void removePermission(String permission) {
        this.permissions.remove(permission);
    }
    
    /**
     * Soft delete do usuário
     */
    public void softDelete() {
        this.deleted = true;
        this.deletedAt = LocalDateTime.now();
        this.active = false;
        // Adiciona sufixo para evitar conflitos com novos usuários
        var currenteTimeMillis = System.currentTimeMillis();
        this.username = "deleted_" + this.username + "_" + currenteTimeMillis;
        this.email = "deleted_" + this.email + "_" + currenteTimeMillis;
    }
    
    /**
     * Restaura usuário deletado
     */
    public void restore() {
        this.deleted = false;
        this.deletedAt = null;
        this.active = true;

        // Restaurar username removendo prefixo e qualquer sufixo numérico
        if (this.username.startsWith("deleted_")) {
            String original = this.username.substring("deleted_".length());
            // Remove "_<número>" no final, se existir
            original = original.replaceFirst("_[0-9]+$", "");
            this.username = original;
        }

        // Restaurar email removendo prefixo e sufixo numérico
        if (this.email.startsWith("deleted_")) {
            String original = this.email.substring("deleted_".length());
            original = original.replaceFirst("_[0-9]+$", "");
            this.email = original;
        }
    }

    
    /**
     * Altera a senha do usuário
     */
    public void changePassword(String newPassword) {
        this.password = newPassword;
        this.passwordChangedAt = LocalDateTime.now();
        this.mustChangePassword = false;
    }
    
    /**
     * Verifica se a senha precisa ser alterada
     */
    public boolean isPasswordExpired() {
        if (this.passwordChangedAt == null) {
            return true;
        }
        // Senha expira após 90 dias
        return this.passwordChangedAt.plusDays(90).isBefore(LocalDateTime.now());
    }
}