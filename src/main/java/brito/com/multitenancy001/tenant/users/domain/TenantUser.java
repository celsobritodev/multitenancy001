// src/main/java/brito/com/multitenancy001/tenant/users/domain/TenantUser.java
package brito.com.multitenancy001.tenant.users.domain;

import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import brito.com.multitenancy001.shared.domain.audit.AuditInfo;
import brito.com.multitenancy001.shared.domain.audit.Auditable;
import brito.com.multitenancy001.shared.domain.audit.SoftDeletable;
import brito.com.multitenancy001.shared.domain.audit.jpa.AuditEntityListener;
import brito.com.multitenancy001.shared.domain.common.EntityOrigin;
import brito.com.multitenancy001.shared.security.PermissionScopeValidator;
import brito.com.multitenancy001.tenant.security.TenantPermission;
import brito.com.multitenancy001.tenant.security.TenantRole;
import brito.com.multitenancy001.tenant.users.domain.permission.TenantUserPermission;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "tenant_users")
@EntityListeners(AuditEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantUser implements Auditable, SoftDeletable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ==========
    // IDENTITY
    // ==========
    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, columnDefinition = "citext")
    private String email;

    // ==========
    // RBAC
    // ==========
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private TenantRole role;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "tenant_user_permissions",
            joinColumns = @JoinColumn(name = "tenant_user_id")
    )
    @Column(name = "permission", nullable = false, length = 120)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Set<TenantPermission> permissions = new LinkedHashSet<>();

    // ==========
    // PROFILE
    // ==========
    @Column(length = 20)
    private String phone;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(length = 50)
    private String timezone;

    @Column(length = 10)
    private String locale;

    // ==========
    // AUTH
    // ==========
    @Column(nullable = false, length = 200)
    private String password;

    @Column(name = "must_change_password", nullable = false)
    @Builder.Default
    private boolean mustChangePassword = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EntityOrigin origin = EntityOrigin.ADMIN;

    // ==========
    // SECURITY
    // ==========
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

    // ==========
    // STATUS
    // ==========
    @Column(name = "suspended_by_account", nullable = false)
    @Builder.Default
    private boolean suspendedByAccount = false;

    @Column(name = "suspended_by_admin", nullable = false)
    @Builder.Default
    private boolean suspendedByAdmin = false;

    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    // ==========
    // AUDIT
    // ==========
    @Embedded
    @Builder.Default
    private AuditInfo audit = new AuditInfo();

    // ==========
    // DOMAIN HELPERS (DDD puro)
    // ==========

    public void normalizeEmail() {
        /* Normaliza email para login/identidade. */
        this.email = EmailNormalizer.normalizeOrNull(this.email);
    }

    /**
     * Enabled no sentido de domínio (sem considerar lock).
     */
    public boolean isEnabledDomain() {
        /* Enabled = não deletado e não suspenso. */
        return !deleted && !suspendedByAccount && !suspendedByAdmin;
    }

    /**
     * ✅ Regra de lock com "now" explícito (AppClock é a fonte única).
     */
    public boolean isAccountNonLockedAt(Instant now) {
        /* Avalia lock com base no instante informado (AppClock). */
        if (now == null) throw new IllegalArgumentException("now is required");
        return lockedUntil == null || !now.isBefore(lockedUntil);
    }

    /**
     * ✅ Helper de login (enabled + não locked) com "now" explícito.
     */
    public boolean isEnabledForLoginAt(Instant now) {
        /* Regra de login: enabled e não locked no instante informado. */
        return isEnabledDomain() && isAccountNonLockedAt(now);
    }

    public Set<TenantPermission> getPermissions() {
        /* Valida e devolve permissões tipadas no escopo TEN_. */
        if (permissions == null) return Set.of();
        return PermissionScopeValidator.validateTenantPermissionsStrict(permissions);
    }

    public void grantPermission(TenantPermission permission) {
        /* Concede permissão explícita ao usuário (fail-fast no escopo). */
        if (permission == null) return;
        if (this.permissions == null) this.permissions = new LinkedHashSet<>();

        Set<TenantPermission> tmp = new LinkedHashSet<>(this.permissions);
        tmp.add(permission);

        this.permissions = new LinkedHashSet<>(PermissionScopeValidator.validateTenantPermissionsStrict(tmp));
    }

    public void revokePermission(TenantPermission permission) {
        /* Revoga permissão explícita do usuário. */
        if (permission == null) return;
        if (this.permissions == null || this.permissions.isEmpty()) return;
        this.permissions.remove(permission);
    }

    /**
     * ✅ Mantido só para leitura/compat (ex.: DTO legado).
     */
    public Set<TenantUserPermission> getExplicitPermissions() {
        /* Exporta permissões explícitas como "TEN_*" (compat). */
        if (permissions == null || permissions.isEmpty()) return Set.of();
        LinkedHashSet<TenantUserPermission> out = new LinkedHashSet<>();
        for (TenantPermission p : permissions) {
            if (p == null) continue;
            out.add(new TenantUserPermission(p.asAuthority()));
        }
        return out;
    }

    // ==========
    // Password reset (compat com call-sites)
    // ==========

    public void setPasswordResetExpires(Instant expiresAt) {
        /* Define expiração do token de reset. */
        this.passwordResetExpiresAt = expiresAt;
    }

    public Instant getPasswordResetExpires() {
        /* Retorna expiração do token de reset. */
        return this.passwordResetExpiresAt;
    }

    // ==========
    // Soft delete (DDD + compat)
    // ==========

    public void softDelete() {
        /* Soft delete simples (flag). */
        this.deleted = true;
    }

    /**
     * ✅ Canonical no domínio: soft delete com "now" explícito.
     */
    public void softDeleteAt(Instant now) {
        /* Soft delete com marcação no audit (quando disponível). */
        if (this.deleted) return;
        if (now == null) throw new IllegalArgumentException("now is required");

        this.deleted = true;
        if (this.audit != null) this.audit.markDeleted(now);
    }

    public void restore() {
        /* Restaura (remove delete e limpa audit.deletedAt). */
        this.deleted = false;
        if (this.audit != null) this.audit.clearDeleted();
    }

    public void clearSecurityLockState() {
        /* Limpa lock. */
        this.lockedUntil = null;
    }

    // ==========
    // Optional: helpers de normalização
    // ==========

    public void rename(String newName) {
        /* Renomeia com validação mínima. */
        if (newName == null || newName.isBlank()) throw new IllegalArgumentException("name é obrigatório");
        this.name = newName.trim();
    }

    public void changeEmail(String newEmail) {
        /* Altera email com normalização/validação. */
        String normalized = EmailNormalizer.normalizeOrNull(newEmail);
        if (normalized == null) throw new IllegalArgumentException("email inválido");
        this.email = normalized;
    }

    // ==========
    // Contracts
    // ==========

    @Override
    public boolean isDeleted() {
        /* Soft delete flag. */
        return deleted;
    }

    @Override
    public AuditInfo getAudit() {
        /* Audit embutido. */
        return audit;
    }

    // ==========
    // COMPAT LAYER (para não quebrar call-sites existentes)
    // ==========
    // IMPORTANTE:
    // - NÃO usa Instant.now() aqui.
    // - Apenas delega para os métodos canônicos "*At(Instant now)".
    // - Mantém assinaturas que já existem no código (AuthenticatedUserContext, mappers, services).

    /**
     * COMPAT: assinatura antiga usada por AuthenticatedUserContext.
     */
    public boolean isEnabledForLogin(Instant now) {
        /* Compat para call-sites existentes (sem Instant.now). */
        return isEnabledForLoginAt(now);
    }

    /**
     * COMPAT: assinatura antiga usada por AuthenticatedUserContext.
     */
    public boolean isAccountNonLocked(Instant now) {
        /* Compat para call-sites existentes (sem Instant.now). */
        return isAccountNonLockedAt(now);
    }

    /**
     * COMPAT: assinatura antiga usada por TenantUserApiMapper e outros.
     * (Sem necessidade de clock)
     */
    public boolean isEnabled() {
        /* Compat: enabled de domínio (sem lock). */
        return isEnabledDomain();
    }

    /**
     * COMPAT: assinatura antiga usada por TenantUserCommandService.
     * Observação: epochMillis era redundante; mantemos só para compat e ignoramos.
     */
    public void softDelete(Instant now, long epochMillis) {
        /* Compat para call-sites existentes (ignora epochMillis). */
        softDeleteAt(now);
    }
}