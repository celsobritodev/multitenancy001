package brito.com.multitenancy001.tenant.domain.user;

import brito.com.multitenancy001.shared.validation.ValidationPatterns;
import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "users_tenant",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_user_tenant_account_username",
            columnNames = {"account_id", "username"}
        )
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "password")
public class TenantUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "password_reset_token", length = 255)
    private String passwordResetToken;

    @Column(name = "password_reset_expires")
    private LocalDateTime passwordResetExpires;

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
    private TenantRole role;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    // ✅ NOVO: suspensão por conta (status da conta)
    @Column(name = "suspended_by_account", nullable = false)
    @Builder.Default
    private boolean suspendedByAccount = false;

    // ✅ NOVO: suspensão manual por admin do tenant
    @Column(name = "suspended_by_admin", nullable = false)
    @Builder.Default
    private boolean suspendedByAdmin = false;

    @ElementCollection
    @CollectionTable(
        name = "user_tenant_permissions",
        joinColumns = @JoinColumn(name = "user_tenant_id")
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

    @Column(name = "avatar_url", length = 500)
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

    @PrePersist
    protected void onCreate() {
        if (username != null) username = username.toLowerCase().trim();
        if (permissions == null) permissions = new ArrayList<>();
        if (permissions.isEmpty()) addDefaultPermissions();
    }

    private void addDefaultPermissions() {
        switch (role) {
            case TENANT_ADMIN -> permissions.addAll(List.of(
                "USER_CREATE","USER_UPDATE","USER_DELETE","USER_VIEW",
                "PRODUCT_CREATE","PRODUCT_UPDATE","PRODUCT_DELETE","PRODUCT_VIEW",
                "SALE_CREATE","SALE_UPDATE","SALE_DELETE","SALE_VIEW",
                "REPORT_VIEW","SETTINGS_MANAGE"
            ));
            case MANAGER -> permissions.addAll(List.of(
                "PRODUCT_CREATE","PRODUCT_UPDATE","PRODUCT_VIEW",
                "SALE_CREATE","SALE_VIEW","REPORT_VIEW"
            ));
            case VIEWER -> permissions.addAll(List.of(
                "USER_VIEW","PRODUCT_VIEW","SALE_VIEW","REPORT_VIEW"
            ));
            case USER -> permissions.add("VIEW_BASIC");
        }
    }

    public boolean isEnabledForLogin(LocalDateTime now) {
        if (lockedUntil != null && lockedUntil.isAfter(now)) return false;
        return !deleted && !suspendedByAccount && !suspendedByAdmin;
    }


    public void softDelete() {
        if (deleted) return;
        deleted = true;
        deletedAt = LocalDateTime.now();

        // ✅ deletado sempre bloqueia login
        suspendedByAccount = true;
        suspendedByAdmin = true;

        long ts = System.currentTimeMillis();
        username = "deleted_" + username + "_" + ts;
        email = "deleted_" + email + "_" + ts;
    }

    public void restore() {
        if (!deleted) return;
        deleted = false;
        deletedAt = null;

        // ✅ ao restaurar: volta “desbloqueado” pela lógica normal
        // (conta ativa vai controlar suspendedByAccount; admin controla suspendedByAdmin)
        suspendedByAdmin = false;
        // não força false em suspendedByAccount aqui — quem manda é o status da conta
    }
}
