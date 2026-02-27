// src/main/java/brito/com/multitenancy001/tenant/users/app/command/TenantUserCommandService.java
package brito.com.multitenancy001.tenant.users.app.command;

import brito.com.multitenancy001.infrastructure.persistence.tx.AfterCommit;
import brito.com.multitenancy001.infrastructure.publicschema.audit.PublicAuditDispatcher;
import brito.com.multitenancy001.infrastructure.publicschema.audit.SecurityAuditService;
import brito.com.multitenancy001.infrastructure.security.SecurityUtils;
import brito.com.multitenancy001.infrastructure.tenant.TenantSchemaUnitOfWork;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;
import brito.com.multitenancy001.shared.domain.common.EntityOrigin;
import brito.com.multitenancy001.shared.json.JsonDetailsMapper;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.persistence.publicschema.LoginIdentityProvisioningService;
import brito.com.multitenancy001.shared.security.PermissionScopeValidator;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.shared.validation.ValidationPatterns;
import brito.com.multitenancy001.tenant.security.TenantPermission;
import brito.com.multitenancy001.tenant.security.TenantRole;
import brito.com.multitenancy001.tenant.security.TenantRolePermissions;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import brito.com.multitenancy001.tenant.users.persistence.TenantUserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Casos de uso de comando para usuários do Tenant.
 *
 * <p>Auditoria SOC2-like:</p>
 * <ul>
 *   <li>ATTEMPT + SUCCESS/FAIL/DENIED (append-only em public schema).</li>
 *   <li>Details estruturado (Map -> JSON), sem segredos.</li>
 *   <li>Gravação PUBLIC protegida contra nesting ilegal TENANT->PUBLIC via {@link PublicAuditDispatcher}.</li>
 * </ul>
 *
 * <p>Regra crítica (multi-tx managers):</p>
 * <ul>
 *   <li>NUNCA iniciar TX PUBLIC dentro de TX TENANT no mesmo thread.</li>
 *   <li>Este service usa {@link PublicAuditDispatcher} para gravar em PUBLIC de forma segura.</li>
 *   <li>Indexação de login_identities (PUBLIC) roda via {@link AfterCommit} (pós-commit do tenant).</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantUserCommandService {

    private static final String SCOPE = "TENANT";

    private final TenantUserRepository tenantUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppClock appClock;

    /** Dispatcher que evita nesting ilegal TENANT->PUBLIC. */
    private final PublicAuditDispatcher publicAuditDispatcher;

    /**
     * Unit of Work que garante:
     * - bind do tenantSchema
     * - transação tenant (em um único contrato)
     */
    private final TenantSchemaUnitOfWork tenantSchemaUnitOfWork;

    /** Mantido por compat (best-effort actor). */
    private final SecurityUtils securityUtils;

    /** Auditoria append-only (public schema). */
    private final SecurityAuditService securityAuditService;

    /** Mapper para details (Map/record/String -> JsonNode). */
    private final JsonDetailsMapper jsonDetailsMapper;

    /** Provisionamento do índice público para login por e-mail (PUBLIC schema). */
    private final LoginIdentityProvisioningService loginIdentityProvisioningService;

    // =========================================================
    // CREATE
    // =========================================================

    /**
     * Cria um usuário de tenant, aplicando permissões finais (base + requested validadas).
     *
     * <p>Auditoria:</p>
     * <ul>
     *   <li>USER_CREATED: ATTEMPT + SUCCESS/FAIL/DENIED</li>
     *   <li>PERMISSIONS_CHANGED: SUCCESS (reason=create)</li>
     * </ul>
     *
     * <p>Index público (login_identities):</p>
     * <ul>
     *   <li>Após COMMIT do tenant, garante {@code (email, accountId)} no PUBLIC para permitir loginInit ambíguo.</li>
     * </ul>
     */
    public TenantUser createTenantUser(
            Long accountId,
            String tenantSchema,
            String name,
            String email,
            String rawPassword,
            TenantRole role,
            String phone,
            String avatarUrl,
            String locale,
            String timezone,
            LinkedHashSet<TenantPermission> requestedPermissions,
            Boolean mustChangePassword,
            EntityOrigin origin
    ) {
        return tenantSchemaUnitOfWork.tx(tenantSchema, () -> {

            if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);
            if (!StringUtils.hasText(tenantSchema)) throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatório", 400);

            if (!StringUtils.hasText(name)) throw new ApiException(ApiErrorCode.INVALID_NAME, "Nome é obrigatório", 400);
            if (!StringUtils.hasText(email)) throw new ApiException(ApiErrorCode.INVALID_EMAIL, "Email é obrigatório", 400);
            if (!StringUtils.hasText(rawPassword)) throw new ApiException(ApiErrorCode.INVALID_PASSWORD, "Senha é obrigatória", 400);
            if (role == null) throw new ApiException(ApiErrorCode.INVALID_ROLE, "Role é obrigatória", 400);

            String normEmail = EmailNormalizer.normalizeOrNull(email);
            if (!StringUtils.hasText(normEmail) || !normEmail.matches(ValidationPatterns.EMAIL_PATTERN)) {
                throw new ApiException(ApiErrorCode.INVALID_EMAIL, "Email inválido", 400);
            }
            if (!rawPassword.matches(ValidationPatterns.PASSWORD_PATTERN)) {
                throw new ApiException(ApiErrorCode.WEAK_PASSWORD, "Senha fraca", 400);
            }

            final Actor actor = resolveActorOrNull(accountId, tenantSchema);
            final int requestedCount = (requestedPermissions == null) ? 0 : requestedPermissions.size();

            final Map<String, Object> attemptDetails = m(
                    "scope", SCOPE,
                    "stage", "before_save",
                    "role", role.name(),
                    "requestedPermissionsCount", requestedCount
            );

            // ATTEMPT
            recordAudit(SecurityAuditActionType.USER_CREATED, AuditOutcome.ATTEMPT, actor, normEmail, null, accountId, tenantSchema, attemptDetails);

            try {
                boolean exists = tenantUserRepository.existsByEmailAndAccountId(normEmail, accountId);
                if (exists) throw new ApiException(ApiErrorCode.EMAIL_ALREADY_EXISTS, "Email já cadastrado nesta conta", 409);

                TenantUser user = new TenantUser();
                user.setAccountId(accountId);

                user.rename(name);
                user.changeEmail(normEmail);

                user.setPassword(passwordEncoder.encode(rawPassword));
                user.setRole(role);

                user.setOrigin(origin == null ? EntityOrigin.ADMIN : origin);
                user.setMustChangePassword(Boolean.TRUE.equals(mustChangePassword));

                Instant now = appClock.instant();
                user.setPasswordChangedAt(user.isMustChangePassword() ? null : now);

                user.setPhone(StringUtils.hasText(phone) ? phone.trim() : null);
                user.setAvatarUrl(StringUtils.hasText(avatarUrl) ? avatarUrl.trim() : null);

                user.setLocale(StringUtils.hasText(locale) ? locale.trim() : null);
                user.setTimezone(StringUtils.hasText(timezone) ? timezone.trim() : null);

                user.setSuspendedByAccount(false);
                user.setSuspendedByAdmin(false);

                Set<TenantPermission> base = new LinkedHashSet<>(TenantRolePermissions.permissionsFor(role));
                Set<TenantPermission> desired = new LinkedHashSet<>();
                if (requestedPermissions != null && !requestedPermissions.isEmpty()) desired.addAll(requestedPermissions);

                desired = PermissionScopeValidator.validateTenantPermissionsStrict(desired);

                Set<TenantPermission> finalPerms = new LinkedHashSet<>(base);
                finalPerms.addAll(desired);

                user.setPermissions(finalPerms);

                if (!StringUtils.hasText(user.getLocale())) user.setLocale("pt_BR");
                if (!StringUtils.hasText(user.getTimezone())) user.setTimezone("America/Sao_Paulo");

                TenantUser saved = tenantUserRepository.save(user);

                // SUCCESS (com targetUserId correto)
                recordAudit(
                        SecurityAuditActionType.USER_CREATED,
                        AuditOutcome.SUCCESS,
                        actor,
                        saved.getEmail(),
                        saved.getId(),
                        accountId,
                        tenantSchema,
                        m(
                                "scope", SCOPE,
                                "stage", "after_save",
                                "role", role.name(),
                                "finalPermissionsCount", sizeOrZero(saved.getPermissions())
                        )
                );

                // Permissions changed (create): base + requested -> final
                recordAudit(
                        SecurityAuditActionType.PERMISSIONS_CHANGED,
                        AuditOutcome.SUCCESS,
                        actor,
                        saved.getEmail(),
                        saved.getId(),
                        accountId,
                        tenantSchema,
                        m(
                                "scope", SCOPE,
                                "reason", "create",
                                "baseCount", base.size(),
                                "requestedCount", requestedCount,
                                "finalCount", finalPerms.size()
                        )
                );

                // ✅ Pós-commit: garante índice público (login_identities) para permitir loginInit ambíguo.
                AfterCommit.run(() -> loginIdentityProvisioningService.ensureTenantIdentityAfterCompletion(saved.getEmail(), accountId));

                return saved;

            } catch (ApiException ex) {
                recordAudit(SecurityAuditActionType.USER_CREATED, outcomeFrom(ex), actor, normEmail, null, accountId, tenantSchema, failureDetails(SCOPE, ex));
                throw ex;
            } catch (Exception ex) {
                recordAudit(SecurityAuditActionType.USER_CREATED, AuditOutcome.FAILURE, actor, normEmail, null, accountId, tenantSchema, unexpectedFailureDetails(SCOPE, ex));
                throw ex;
            }
        });
    }

    // =========================================================
    // UPDATE: STATUS / PROFILE
    // =========================================================

    public void setSuspendedByAdmin(Long accountId, String tenantSchema, Long userId, boolean suspended) {
        setSuspension(accountId, tenantSchema, userId, suspended, true);
    }

    public void setSuspendedByAccount(Long accountId, String tenantSchema, Long userId, boolean suspended) {
        setSuspension(accountId, tenantSchema, userId, suspended, false);
    }

    private void setSuspension(Long accountId, String tenantSchema, Long userId, boolean suspended, boolean byAdmin) {
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);
        if (!StringUtils.hasText(tenantSchema)) throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatório", 400);
        if (userId == null) throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatório", 400);

        tenantSchemaUnitOfWork.tx(tenantSchema, () -> {
            Actor actor = resolveActorOrNull(accountId, tenantSchema);

            TenantUser user = tenantUserRepository.findByIdAndAccountIdAndDeletedFalse(userId, accountId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário não encontrado", 404));

            requireNotBuiltInForMutation(user, "Não é permitido suspender usuário BUILT_IN");

            if (suspended && isActiveOwner(user)) {
                requireWillStillHaveAtLeastOneActiveOwner(accountId,
                        "Não é permitido suspender o último TENANT_OWNER ativo");
            }

            SecurityAuditActionType action = suspended ? SecurityAuditActionType.USER_SUSPENDED : SecurityAuditActionType.USER_RESTORED;
            String reason = byAdmin ? "suspendedByAdmin" : "suspendedByAccount";

            Map<String, Object> details = m(
                    "scope", SCOPE,
                    "reason", reason,
                    "suspended", suspended
            );

            auditAttemptSuccessFail(
                    action,
                    actor,
                    user.getEmail(),
                    user.getId(),
                    accountId,
                    tenantSchema,
                    details,
                    null,
                    () -> {
                        int updated = byAdmin
                                ? tenantUserRepository.setSuspendedByAdmin(accountId, userId, suspended)
                                : tenantUserRepository.setSuspendedByAccount(accountId, userId, suspended);

                        if (updated == 0) throw new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário não encontrado", 404);
                        return null;
                    }
            );

            return null;
        });
    }

    public TenantUser updateProfile(
            Long userId,
            Long accountId,
            String tenantSchema,
            String name,
            String phone,
            String avatarUrl,
            String locale,
            String timezone,
            Instant now // compat (ignorado; usamos AppClock)
    ) {
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);
        if (!StringUtils.hasText(tenantSchema)) throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatório", 400);
        if (userId == null) throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatório", 400);

        return tenantSchemaUnitOfWork.tx(tenantSchema, () -> {
            Actor actor = resolveActorOrNull(accountId, tenantSchema);

            TenantUser user = tenantUserRepository.findByIdAndAccountIdAndDeletedFalse(userId, accountId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário não encontrado", 404));

            requireNotBuiltInForMutation(user, "Não é permitido alterar perfil de usuário BUILT_IN");

            Map<String, Object> attemptDetails = m(
                    "scope", SCOPE,
                    "reason", "updateProfile"
            );

            recordAudit(
                    SecurityAuditActionType.USER_UPDATED,
                    AuditOutcome.ATTEMPT,
                    actor,
                    user.getEmail(),
                    user.getId(),
                    accountId,
                    tenantSchema,
                    attemptDetails
            );

            try {
                boolean changed = false;
                Map<String, Object> changes = new LinkedHashMap<>();

                if (StringUtils.hasText(name)) {
                    user.rename(name);
                    changed = true;
                    changes.put("name", "changed");
                }
                if (StringUtils.hasText(phone)) {
                    user.setPhone(phone.trim());
                    changed = true;
                    changes.put("phone", "changed");
                }
                if (StringUtils.hasText(locale)) {
                    user.setLocale(locale.trim());
                    changed = true;
                    changes.put("locale", "changed");
                }
                if (StringUtils.hasText(timezone)) {
                    user.setTimezone(timezone.trim());
                    changed = true;
                    changes.put("timezone", "changed");
                }

                if (avatarUrl != null) {
                    String trimmed = avatarUrl.trim();
                    user.setAvatarUrl(trimmed.isEmpty() ? null : trimmed);
                    changed = true;
                    changes.put("avatarUrl", trimmed.isEmpty() ? "cleared" : "changed");
                }

                TenantUser saved = tenantUserRepository.save(user);

                recordAudit(
                        SecurityAuditActionType.USER_UPDATED,
                        AuditOutcome.SUCCESS,
                        actor,
                        saved.getEmail(),
                        saved.getId(),
                        accountId,
                        tenantSchema,
                        m(
                                "scope", SCOPE,
                                "reason", "updateProfile",
                                "changed", changed,
                                "changes", changes
                        )
                );

                return saved;

            } catch (ApiException ex) {
                recordAudit(
                        SecurityAuditActionType.USER_UPDATED,
                        outcomeFrom(ex),
                        actor,
                        user.getEmail(),
                        user.getId(),
                        accountId,
                        tenantSchema,
                        failureDetails(SCOPE, ex)
                );
                throw ex;

            } catch (Exception ex) {
                recordAudit(
                        SecurityAuditActionType.USER_UPDATED,
                        AuditOutcome.FAILURE,
                        actor,
                        user.getEmail(),
                        user.getId(),
                        accountId,
                        tenantSchema,
                        unexpectedFailureDetails(SCOPE, ex)
                );
                throw ex;
            }
        });
    }

    // =========================================================
    // PASSWORD
    // =========================================================

    public TenantUser resetPassword(Long userId, Long accountId, String tenantSchema, String newPassword) {
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);
        if (!StringUtils.hasText(tenantSchema)) throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatório", 400);
        if (userId == null) throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatório", 400);
        if (!StringUtils.hasText(newPassword)) throw new ApiException(ApiErrorCode.INVALID_PASSWORD, "Senha é obrigatória", 400);

        if (!newPassword.matches(ValidationPatterns.PASSWORD_PATTERN)) {
            throw new ApiException(ApiErrorCode.WEAK_PASSWORD, "Senha fraca", 400);
        }

        return tenantSchemaUnitOfWork.tx(tenantSchema, () -> {
            Actor actor = resolveActorOrNull(accountId, tenantSchema);

            TenantUser user = tenantUserRepository.findIncludingDeletedByIdAndAccountId(userId, accountId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário não encontrado", 404));

            Map<String, Object> details = m("scope", SCOPE, "reason", "admin_reset");

            return auditAttemptSuccessFail(
                    SecurityAuditActionType.PASSWORD_RESET_COMPLETED,
                    actor,
                    user.getEmail(),
                    user.getId(),
                    accountId,
                    tenantSchema,
                    details,
                    null,
                    () -> {
                        Instant now = appClock.instant();

                        user.setPassword(passwordEncoder.encode(newPassword));
                        user.setMustChangePassword(false);
                        user.setPasswordChangedAt(now);
                        user.setPasswordResetToken(null);
                        user.setPasswordResetExpires(null);

                        return tenantUserRepository.save(user);
                    }
            );
        });
    }

    public void resetPasswordWithToken(Long accountId, String tenantSchema, String email, String token, String newPassword) {
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);
        if (!StringUtils.hasText(tenantSchema)) throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatório", 400);
        if (!StringUtils.hasText(token)) throw new ApiException(ApiErrorCode.TOKEN_REQUIRED, "token é obrigatório", 400);
        if (!StringUtils.hasText(newPassword)) throw new ApiException(ApiErrorCode.INVALID_PASSWORD, "Senha é obrigatória", 400);

        if (!newPassword.matches(ValidationPatterns.PASSWORD_PATTERN)) {
            throw new ApiException(ApiErrorCode.WEAK_PASSWORD, "Senha fraca", 400);
        }

        tenantSchemaUnitOfWork.tx(tenantSchema, () -> {
            TenantUser user = tenantUserRepository.findByPasswordResetTokenAndAccountId(token, accountId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.TOKEN_INVALID, "Token inválido", 400));

            Actor actor = resolveActorOrNull(accountId, tenantSchema);
            Map<String, Object> attempt = m("scope", SCOPE, "flow", "token_reset");

            auditAttemptSuccessFail(
                    SecurityAuditActionType.PASSWORD_RESET_COMPLETED,
                    actor,
                    user.getEmail(),
                    user.getId(),
                    accountId,
                    tenantSchema,
                    attempt,
                    null,
                    () -> {
                        Instant now = appClock.instant();

                        if (user.getPasswordResetExpires() == null || user.getPasswordResetExpires().isBefore(now)) {
                            throw new ApiException(ApiErrorCode.TOKEN_EXPIRED, "Token expirado", 400);
                        }

                        if (StringUtils.hasText(email) && user.getEmail() != null) {
                            String tokenLogin = EmailNormalizer.normalizeOrNull(email);
                            String userEmail = EmailNormalizer.normalizeOrNull(user.getEmail());

                            if (!StringUtils.hasText(tokenLogin) || !StringUtils.hasText(userEmail) || !userEmail.equals(tokenLogin)) {
                                throw new ApiException(ApiErrorCode.TOKEN_INVALID, "Token inválido", 400);
                            }
                        }

                        user.setPassword(passwordEncoder.encode(newPassword));
                        user.setMustChangePassword(false);
                        user.setPasswordChangedAt(now);

                        user.setPasswordResetToken(null);
                        user.setPasswordResetExpires(null);

                        tenantUserRepository.save(user);
                        return null;
                    }
            );

            return null;
        });
    }

    public void changeMyPassword(Long userId, Long accountId, String tenantSchema, String currentPassword, String newPassword, String confirmNewPassword) {
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);
        if (!StringUtils.hasText(tenantSchema)) throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatório", 400);
        if (userId == null) throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatório", 400);

        if (!StringUtils.hasText(currentPassword)) throw new ApiException(ApiErrorCode.CURRENT_PASSWORD_REQUIRED, "Senha atual é obrigatória", 400);
        if (!StringUtils.hasText(newPassword)) throw new ApiException(ApiErrorCode.NEW_PASSWORD_REQUIRED, "Nova senha é obrigatória", 400);
        if (!StringUtils.hasText(confirmNewPassword)) throw new ApiException(ApiErrorCode.CONFIRM_PASSWORD_REQUIRED, "Confirmar nova senha é obrigatório", 400);

        if (!newPassword.equals(confirmNewPassword)) throw new ApiException(ApiErrorCode.PASSWORD_MISMATCH, "Nova senha e confirmação não conferem", 400);
        if (!newPassword.matches(ValidationPatterns.PASSWORD_PATTERN)) throw new ApiException(ApiErrorCode.WEAK_PASSWORD, "Senha fraca", 400);

        tenantSchemaUnitOfWork.tx(tenantSchema, () -> {
            TenantUser user = tenantUserRepository.findByIdAndAccountIdAndDeletedFalse(userId, accountId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário não encontrado", 404));

            Actor actor = resolveActorOrNull(accountId, tenantSchema);
            Map<String, Object> details = m("scope", SCOPE, "reason", "self_change");

            auditAttemptSuccessFail(
                    SecurityAuditActionType.PASSWORD_CHANGED,
                    actor,
                    user.getEmail(),
                    user.getId(),
                    accountId,
                    tenantSchema,
                    details,
                    null,
                    () -> {
                        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
                            throw new ApiException(ApiErrorCode.CURRENT_PASSWORD_INVALID, "Senha atual inválida", 400);
                        }

                        Instant now = appClock.instant();

                        user.setPassword(passwordEncoder.encode(newPassword));
                        user.setMustChangePassword(false);
                        user.setPasswordChangedAt(now);

                        user.setPasswordResetToken(null);
                        user.setPasswordResetExpires(null);

                        tenantUserRepository.save(user);
                        return null;
                    }
            );

            return null;
        });
    }

    // =========================================================
    // DELETE / RESTORE
    // =========================================================

    public void softDelete(Long userId, Long accountId, String tenantSchema) {
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);
        if (!StringUtils.hasText(tenantSchema)) throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatório", 400);
        if (userId == null) throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatório", 400);

        tenantSchemaUnitOfWork.tx(tenantSchema, () -> {
            Actor actor = resolveActorOrNull(accountId, tenantSchema);

            TenantUser user = tenantUserRepository.findIncludingDeletedByIdAndAccountId(userId, accountId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário não encontrado", 404));

            if (user.isDeleted()) return null;

            requireNotBuiltInForMutation(user, "Não é permitido excluir usuário BUILT_IN");

            if (isActiveOwner(user)) {
                requireWillStillHaveAtLeastOneActiveOwner(accountId,
                        "Não é permitido excluir o último TENANT_OWNER ativo");
            }

            final String emailForIndex = user.getEmail();

            Map<String, Object> details = m("scope", SCOPE, "reason", "softDelete");

            auditAttemptSuccessFail(
                    SecurityAuditActionType.USER_SOFT_DELETED,
                    actor,
                    user.getEmail(),
                    user.getId(),
                    accountId,
                    tenantSchema,
                    details,
                    null,
                    () -> {
                        Instant now = appClock.instant();
                        user.softDelete(now, appClock.epochMillis());
                        tenantUserRepository.save(user);
                        return null;
                    }
            );

            // ✅ Pós-commit: remove do índice público
            AfterCommit.run(() -> loginIdentityProvisioningService.deleteTenantIdentityAfterCompletion(emailForIndex, accountId));

            return null;
        });
    }

    public TenantUser restore(Long userId, Long accountId, String tenantSchema) {
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);
        if (!StringUtils.hasText(tenantSchema)) throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatório", 400);
        if (userId == null) throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatório", 400);

        return tenantSchemaUnitOfWork.tx(tenantSchema, () -> {
            Actor actor = resolveActorOrNull(accountId, tenantSchema);

            TenantUser user = tenantUserRepository.findIncludingDeletedByIdAndAccountId(userId, accountId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário não encontrado", 404));

            Map<String, Object> details = m("scope", SCOPE, "reason", "softRestore");

            TenantUser saved = auditAttemptSuccessFail(
                    SecurityAuditActionType.USER_SOFT_RESTORED,
                    actor,
                    user.getEmail(),
                    user.getId(),
                    accountId,
                    tenantSchema,
                    details,
                    null,
                    () -> {
                        user.restore();
                        return tenantUserRepository.save(user);
                    }
            );

            // ✅ Pós-commit: garante índice público novamente
            AfterCommit.run(() -> loginIdentityProvisioningService.ensureTenantIdentityAfterCompletion(saved.getEmail(), accountId));

            return saved;
        });
    }

    public void hardDelete(Long userId, Long accountId, String tenantSchema) {
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);
        if (!StringUtils.hasText(tenantSchema)) throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatório", 400);
        if (userId == null) throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatório", 400);

        tenantSchemaUnitOfWork.tx(tenantSchema, () -> {
            TenantUser user = tenantUserRepository.findIncludingDeletedByIdAndAccountId(userId, accountId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário não encontrado", 404));

            requireNotBuiltInForMutation(user, "Não é permitido hard-delete de usuário BUILT_IN");

            if (!user.isDeleted() && isActiveOwner(user)) {
                requireWillStillHaveAtLeastOneActiveOwner(accountId,
                        "Não é permitido excluir o último TENANT_OWNER ativo");
            }

            final String emailForIndex = user.getEmail();

            tenantUserRepository.delete(user);

            // ✅ Pós-commit: remove do índice público
            AfterCommit.run(() -> loginIdentityProvisioningService.deleteTenantIdentityAfterCompletion(emailForIndex, accountId));

            return null;
        });
    }

    public TenantUser save(String tenantSchema, TenantUser user) {
        if (!StringUtils.hasText(tenantSchema)) throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatório", 400);
        if (user == null) throw new ApiException(ApiErrorCode.INVALID_REQUEST, "Usuário inválido", 400);
        return tenantSchemaUnitOfWork.tx(tenantSchema, () -> tenantUserRepository.save(user));
    }

    // =========================================================
    // ROLE TRANSFER (OWNER)
    // =========================================================

    public void transferTenantOwnerRole(Long accountId, String tenantSchema, Long fromUserId, Long toUserId) {
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);
        if (!StringUtils.hasText(tenantSchema)) throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatório", 400);
        if (fromUserId == null) throw new ApiException(ApiErrorCode.FROM_USER_REQUIRED, "fromUserId é obrigatório", 400);
        if (toUserId == null) throw new ApiException(ApiErrorCode.TO_USER_REQUIRED, "toUserId é obrigatório", 400);
        if (fromUserId.equals(toUserId)) throw new ApiException(ApiErrorCode.INVALID_TRANSFER, "Não é possível transferir para si mesmo", 400);

        tenantSchemaUnitOfWork.tx(tenantSchema, () -> {
            Actor actor = resolveActorOrNull(accountId, tenantSchema);

            TenantUser from = tenantUserRepository.findEnabledByIdAndAccountId(fromUserId, accountId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário origem não encontrado/habilitado", 404));
            requireNotBuiltInForMutation(from, "Não é permitido transferir ownership a partir de usuário BUILT_IN");

            if (from.getRole() == null || !from.getRole().isTenantOwner()) {
                throw new ApiException(ApiErrorCode.FORBIDDEN, "Apenas o TENANT_OWNER pode transferir", 403);
            }

            TenantUser to = tenantUserRepository.findEnabledByIdAndAccountId(toUserId, accountId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário destino não encontrado/habilitado", 404));
            requireNotBuiltInForMutation(to, "Não é permitido transferir ownership para usuário BUILT_IN");

            TenantRole beforeFrom = from.getRole();
            TenantRole beforeTo = to.getRole();

            recordAudit(
                    SecurityAuditActionType.OWNERSHIP_TRANSFERRED,
                    AuditOutcome.ATTEMPT,
                    actor,
                    null,
                    null,
                    accountId,
                    tenantSchema,
                    m(
                            "scope", SCOPE,
                            "fromUserId", from.getId(),
                            "fromEmail", from.getEmail(),
                            "toUserId", to.getId(),
                            "toEmail", to.getEmail()
                    )
            );

            recordAudit(SecurityAuditActionType.ROLE_CHANGED, AuditOutcome.ATTEMPT, actor, from.getEmail(), from.getId(), accountId, tenantSchema,
                    m("scope", SCOPE, "kind", "transferOwner", "side", "from"));
            recordAudit(SecurityAuditActionType.ROLE_CHANGED, AuditOutcome.ATTEMPT, actor, to.getEmail(), to.getId(), accountId, tenantSchema,
                    m("scope", SCOPE, "kind", "transferOwner", "side", "to"));

            try {
                from.setRole(TenantRole.TENANT_ADMIN);
                to.setRole(TenantRole.TENANT_OWNER);

                from.setPermissions(new LinkedHashSet<>(TenantRolePermissions.permissionsFor(from.getRole())));
                to.setPermissions(new LinkedHashSet<>(TenantRolePermissions.permissionsFor(to.getRole())));

                tenantUserRepository.save(from);
                tenantUserRepository.save(to);

                recordAudit(SecurityAuditActionType.ROLE_CHANGED, AuditOutcome.SUCCESS, actor, from.getEmail(), from.getId(), accountId, tenantSchema,
                        m("scope", SCOPE, "kind", "transferOwner", "side", "from", "from", nameOrNull(beforeFrom), "to", nameOrNull(from.getRole())));
                recordAudit(SecurityAuditActionType.ROLE_CHANGED, AuditOutcome.SUCCESS, actor, to.getEmail(), to.getId(), accountId, tenantSchema,
                        m("scope", SCOPE, "kind", "transferOwner", "side", "to", "from", nameOrNull(beforeTo), "to", nameOrNull(to.getRole())));

                recordAudit(SecurityAuditActionType.PERMISSIONS_CHANGED, AuditOutcome.SUCCESS, actor, from.getEmail(), from.getId(), accountId, tenantSchema,
                        m("scope", SCOPE, "reason", "transferOwner", "side", "from", "finalCount", sizeOrZero(from.getPermissions())));
                recordAudit(SecurityAuditActionType.PERMISSIONS_CHANGED, AuditOutcome.SUCCESS, actor, to.getEmail(), to.getId(), accountId, tenantSchema,
                        m("scope", SCOPE, "reason", "transferOwner", "side", "to", "finalCount", sizeOrZero(to.getPermissions())));

                recordAudit(
                        SecurityAuditActionType.OWNERSHIP_TRANSFERRED,
                        AuditOutcome.SUCCESS,
                        actor,
                        null,
                        null,
                        accountId,
                        tenantSchema,
                        m(
                                "scope", SCOPE,
                                "fromUserId", from.getId(),
                                "fromEmail", from.getEmail(),
                                "toUserId", to.getId(),
                                "toEmail", to.getEmail()
                        )
                );

                return null;
            } catch (ApiException ex) {
                recordAudit(SecurityAuditActionType.OWNERSHIP_TRANSFERRED, outcomeFrom(ex), actor, null, null, accountId, tenantSchema, failureDetails(SCOPE, ex));
                throw ex;
            } catch (Exception ex) {
                recordAudit(SecurityAuditActionType.OWNERSHIP_TRANSFERRED, AuditOutcome.FAILURE, actor, null, null, accountId, tenantSchema, unexpectedFailureDetails(SCOPE, ex));
                throw ex;
            }
        });
    }

    // =========================================================
    // HELPERS / GUARDS
    // =========================================================

    private void requireNotBuiltInForMutation(TenantUser user, String message) {
        if (user != null && user.getOrigin() == EntityOrigin.BUILT_IN) {
            throw new ApiException(ApiErrorCode.USER_BUILT_IN_IMMUTABLE, message, 409);
        }
    }

    private boolean isActiveOwner(TenantUser user) {
        if (user == null) return false;
        if (user.isDeleted()) return false;
        if (user.isSuspendedByAccount()) return false;
        if (user.isSuspendedByAdmin()) return false;
        return user.getRole() != null && user.getRole().isTenantOwner();
    }

    private void requireWillStillHaveAtLeastOneActiveOwner(Long accountId, String message) {
        long owners = tenantUserRepository.countActiveOwnersByAccountId(accountId, TenantRole.TENANT_OWNER);
        if (owners <= 1) throw new ApiException(ApiErrorCode.TENANT_OWNER_REQUIRED, message, 409);
    }

    private static int sizeOrZero(Set<?> s) {
        return s == null ? 0 : s.size();
    }

    private static String nameOrNull(Enum<?> e) {
        return e == null ? null : e.name();
    }

    // =========================================================
    // Audit helpers (não vaza senha/token)
    // =========================================================

    @FunctionalInterface
    private interface AuditCallable<T> {
        T call();
    }

    private <T> T auditAttemptSuccessFail(
            SecurityAuditActionType actionType,
            Actor actor,
            String targetEmail,
            Long targetUserId,
            Long accountId,
            String tenantSchema,
            Map<String, Object> attemptDetails,
            Map<String, Object> successDetails,
            AuditCallable<T> block
    ) {
        recordAudit(actionType, AuditOutcome.ATTEMPT, actor, targetEmail, targetUserId, accountId, tenantSchema, attemptDetails);

        try {
            T result = block.call();

            Object sd = (successDetails != null ? successDetails : attemptDetails);
            recordAudit(actionType, AuditOutcome.SUCCESS, actor, targetEmail, targetUserId, accountId, tenantSchema, sd);

            return result;
        } catch (ApiException ex) {
            recordAudit(actionType, outcomeFrom(ex), actor, targetEmail, targetUserId, accountId, tenantSchema, failureDetails(SCOPE, ex));
            throw ex;
        } catch (Exception ex) {
            recordAudit(actionType, AuditOutcome.FAILURE, actor, targetEmail, targetUserId, accountId, tenantSchema, unexpectedFailureDetails(SCOPE, ex));
            throw ex;
        }
    }

    private void recordAudit(
            SecurityAuditActionType actionType,
            AuditOutcome outcome,
            Actor actor,
            String targetEmail,
            Long targetUserId,
            Long accountId,
            String tenantSchema,
            Object details
    ) {
        final String detailsJson = toJson(details);

        publicAuditDispatcher.dispatch(() -> {
            try {
                securityAuditService.record(
                        actionType,
                        outcome,
                        actor == null ? null : actor.email(),
                        actor == null ? null : actor.userId(),
                        targetEmail,
                        targetUserId,
                        accountId,
                        tenantSchema,
                        detailsJson
                );
            } catch (Exception e) {
                log.warn("⚠️ Falha ao gravar SecurityAudit (best-effort) | actionType={} outcome={} accountId={} tenantSchema={} msg={}",
                        actionType, outcome, accountId, tenantSchema, e.getMessage(), e);
            }
        });
    }

    private String toJson(Object details) {
        if (details == null) return null;

        JsonNode node = jsonDetailsMapper.toJsonNode(details);
        if (node == null || node.isNull()) return null;

        return node.toString();
    }

    private Actor resolveActorOrNull(Long accountId, String tenantSchema) {
        try {
            Long actorUserId = securityUtils.getCurrentUserId();
            Long actorAccountId = securityUtils.getCurrentAccountId();

            if (actorUserId == null || actorAccountId == null) return Actor.anonymous();
            if (!actorAccountId.equals(accountId)) return new Actor(actorUserId, null);

            if (!StringUtils.hasText(tenantSchema)) return new Actor(actorUserId, null);

            String actorEmail = tenantSchemaUnitOfWork.readOnly(tenantSchema, () ->
                    tenantUserRepository.findByIdAndAccountIdAndDeletedFalse(actorUserId, accountId)
                            .map(TenantUser::getEmail)
                            .orElse(null)
            );

            return new Actor(actorUserId, actorEmail);
        } catch (Exception ignored) {
            return Actor.anonymous();
        }
    }

    private static AuditOutcome outcomeFrom(ApiException ex) {
        if (ex == null) return AuditOutcome.FAILURE;
        int s = ex.getStatus();
        return (s == 401 || s == 403) ? AuditOutcome.DENIED : AuditOutcome.FAILURE;
    }

    private static Map<String, Object> failureDetails(String scope, ApiException ex) {
        return m(
                "scope", scope,
                "error", ex == null ? null : ex.getError(),
                "status", ex == null ? 0 : ex.getStatus(),
                "message", safeMessage(ex == null ? null : ex.getMessage())
        );
    }

    private static Map<String, Object> unexpectedFailureDetails(String scope, Exception ex) {
        return m(
                "scope", scope,
                "unexpected", ex == null ? null : ex.getClass().getSimpleName(),
                "message", safeMessage(ex == null ? null : ex.getMessage())
        );
    }

    private static String safeMessage(String msg) {
        if (!StringUtils.hasText(msg)) return null;
        return msg.replace("\n", " ").replace("\r", " ").replace("\t", " ").trim();
    }

    private static Map<String, Object> m(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (kv == null) return m;
        for (int i = 0; i + 1 < kv.length; i += 2) {
            Object k = kv[i];
            Object v = kv[i + 1];
            if (k != null) m.put(String.valueOf(k), v);
        }
        return m;
    }

    private record Actor(Long userId, String email) {
        static Actor anonymous() {
            return new Actor(null, null);
        }
    }
}