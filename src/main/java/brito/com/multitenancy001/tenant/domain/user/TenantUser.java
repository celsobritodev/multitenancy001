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
        @UniqueConstraint(name = "uk_users_tenant_username_account", columnNames = {"username", "account_id"}),
        @UniqueConstraint(name = "uk_users_tenant_email_account", columnNames = {"email", "account_id"})
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "password")
public class TenantUser {

    private static final int USERNAME_MAX_LEN = 100;
    private static final int EMAIL_MAX_LEN = 150;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "password_reset_token", length = 255)
    private String passwordResetToken;

    @Column(name = "password_reset_expires")
    private LocalDateTime passwordResetExpires;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = USERNAME_MAX_LEN)
    @Pattern(regexp = ValidationPatterns.USERNAME_PATTERN, message = "Username inválido.")
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = EMAIL_MAX_LEN)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TenantRole role;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "suspended_by_account", nullable = false)
    @Builder.Default
    private boolean suspendedByAccount = false;

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

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "timezone", nullable = false, length = 64,
            columnDefinition = "varchar(64) default 'UTC'")
    @Builder.Default
    private String timezone = "America/Sao_Paulo";

    @Column(name = "locale", nullable = false, length = 10,
            columnDefinition = "varchar(10) default 'pt-BR'")
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

    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @PrePersist
    @PreUpdate
    protected void onSave() {
        if (permissions == null) permissions = new ArrayList<>();
        if (role == null) throw new IllegalStateException("Role is required");

        // Opcional (recomendado): padroniza casing/trim sem tentar "corrigir" regras de username
        if (username != null) username = username.toLowerCase().trim();
        if (email != null) email = email.toLowerCase().trim();

        if (permissions.isEmpty()) addDefaultPermissions();
    }

  private void addDefaultPermissions() {
    switch (role) {

        case TENANT_ADMIN -> permissions.addAll(List.of(
            "TEN_USER_READ","TEN_USER_CREATE","TEN_USER_UPDATE","TEN_USER_SUSPEND","TEN_USER_RESTORE","TEN_USER_DELETE",
            "TEN_ROLE_TRANSFER",
            "TEN_PRODUCT_READ","TEN_PRODUCT_WRITE",
            "TEN_CATEGORY_READ","TEN_CATEGORY_WRITE",
            "TEN_SUPPLIER_READ","TEN_SUPPLIER_WRITE",
            "TEN_SALE_READ","TEN_SALE_WRITE","TEN_SALE_ISSUES_READ",
            "TEN_REPORT_SALES_READ",
            "TEN_BILLING_READ","TEN_BILLING_WRITE",
            "TEN_SETTINGS_READ","TEN_SETTINGS_WRITE"
        ));

        case ADMIN -> permissions.addAll(List.of(
            "TEN_USER_READ","TEN_USER_CREATE","TEN_USER_UPDATE","TEN_USER_SUSPEND","TEN_USER_RESTORE",
            // não dá TEN_USER_DELETE por padrão se você quer restringir exclusões
            "TEN_PRODUCT_READ","TEN_PRODUCT_WRITE",
            "TEN_CATEGORY_READ","TEN_CATEGORY_WRITE",
            "TEN_SUPPLIER_READ","TEN_SUPPLIER_WRITE",
            "TEN_SALE_READ","TEN_SALE_WRITE","TEN_SALE_ISSUES_READ",
            "TEN_REPORT_SALES_READ",
            "TEN_SETTINGS_READ","TEN_SETTINGS_WRITE",
            "TEN_BILLING_READ" // pode ler billing do tenant, se fizer sentido
        ));

        case PRODUCT_MANAGER -> permissions.addAll(List.of(
            "TEN_PRODUCT_READ","TEN_PRODUCT_WRITE",
            "TEN_CATEGORY_READ","TEN_CATEGORY_WRITE",
            "TEN_SUPPLIER_READ","TEN_SUPPLIER_WRITE"
        ));

        case SALES_MANAGER -> permissions.addAll(List.of(
            "TEN_SALE_READ","TEN_SALE_WRITE","TEN_SALE_ISSUES_READ",
            "TEN_REPORT_SALES_READ"
        ));

        case BILLING_ADMIN -> permissions.addAll(List.of(
            "TEN_BILLING_READ","TEN_BILLING_WRITE",
            "TEN_SETTINGS_READ" // se precisa ler dados do tenant
        ));

        case VIEWER -> permissions.addAll(List.of(
            "TEN_PRODUCT_READ",
            "TEN_SALE_READ",
            "TEN_REPORT_SALES_READ"
        ));

        case USER -> permissions.addAll(List.of(
            "TEN_PRODUCT_READ","TEN_PRODUCT_WRITE",
            "TEN_CATEGORY_READ","TEN_CATEGORY_WRITE",
            "TEN_SUPPLIER_READ","TEN_SUPPLIER_WRITE",
            "TEN_SALE_READ"
        ));
    }
}


    public boolean isLocked(LocalDateTime now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public boolean isEnabledNow() {
        return !deleted && !suspendedByAccount && !suspendedByAdmin;
    }

    public boolean isEnabledForLogin(LocalDateTime now) {
        return isEnabledNow() && !isLocked(now);
    }

    public void softDelete() {
        if (deleted) return;

        deleted = true;
        deletedAt = LocalDateTime.now();

        suspendedByAccount = true;
        suspendedByAdmin = true;

        String ts = String.valueOf(System.currentTimeMillis());

        // ✅ username: normaliza para ficar compatível com o pattern e preserva o sufixo (timestamp)
        {
            String prefix = "deleted_";
            String suffix = "_" + ts;

            String middle = (username == null ? "user" : username)
                .toLowerCase()
                .trim()
                .replaceAll("[^a-z0-9._-]", "_")
                .replaceAll("_{2,}", "_")
                .replaceAll("^_|_$", "");

            if (middle.isBlank()) middle = "user";

            int maxMiddleLen = USERNAME_MAX_LEN - prefix.length() - suffix.length();
            if (maxMiddleLen < 1) maxMiddleLen = 1;

            if (middle.length() > maxMiddleLen) {
                middle = middle.substring(0, maxMiddleLen);
                middle = middle.replaceAll("_+$", "");
                if (middle.isBlank()) middle = "u";
            }

            username = prefix + middle + suffix;
        }

        // ✅ email: preserva o sufixo e respeita o tamanho máximo (não precisa normalizar como username)
        {
            String prefix = "deleted_";
            String suffix = "_" + ts;

            String middle = (email == null ? "deleted" : email).trim();

            int maxMiddleLen = EMAIL_MAX_LEN - prefix.length() - suffix.length();
            if (maxMiddleLen < 1) maxMiddleLen = 1;

            if (middle.length() > maxMiddleLen) {
                middle = middle.substring(0, maxMiddleLen);
            }

            email = prefix + middle + suffix;
        }
    }

    public void restore() {
        if (!deleted) return;
        deleted = false;
        deletedAt = null;

        // ao restaurar: admin deixa de bloquear; account status segue mandando
        suspendedByAdmin = false;
        // não altera suspendedByAccount aqui
    }
}
