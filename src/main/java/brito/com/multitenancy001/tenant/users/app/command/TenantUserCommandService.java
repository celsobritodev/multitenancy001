package brito.com.multitenancy001.tenant.users.app.command;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.infrastructure.persistence.TxExecutor;
import brito.com.multitenancy001.infrastructure.publicschema.audit.SecurityAuditService;
import brito.com.multitenancy001.infrastructure.security.SecurityUtils;
import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;
import brito.com.multitenancy001.shared.domain.common.EntityOrigin;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.security.PermissionScopeValidator;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.shared.validation.ValidationPatterns;
import brito.com.multitenancy001.tenant.security.TenantPermission;
import brito.com.multitenancy001.tenant.security.TenantRole;
import brito.com.multitenancy001.tenant.security.TenantRolePermissions;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import brito.com.multitenancy001.tenant.users.persistence.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TenantUserCommandService {

    private final TenantUserRepository tenantUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppClock appClock;
    private final TxExecutor transactionExecutor;

    private final SecurityUtils securityUtils;
    private final SecurityAuditService securityAuditService;

    private static final String USER_BUILT_IN_IMMUTABLE = "USER_BUILT_IN_IMMUTABLE";
    private static final String TENANT_OWNER_REQUIRED = "TENANT_OWNER_REQUIRED";

    // =========================================================
    // CREATE
    // =========================================================

    public TenantUser createTenantUser(
            Long accountId,
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
        return transactionExecutor.inTenantTx(() -> {

            if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório");
            if (!StringUtils.hasText(name)) throw new ApiException(ApiErrorCode.INVALID_NAME);
            if (!StringUtils.hasText(email)) throw new ApiException(ApiErrorCode.INVALID_EMAIL, "Email é obrigatório");
            if (!StringUtils.hasText(rawPassword)) throw new ApiException(ApiErrorCode.INVALID_PASSWORD);
            if (role == null) throw new ApiException(ApiErrorCode.INVALID_ROLE, "Role é obrigatória");

            String normEmail = EmailNormalizer.normalizeOrNull(email);
            if (!StringUtils.hasText(normEmail) || !normEmail.matches(ValidationPatterns.EMAIL_PATTERN)) {
                throw new ApiException(ApiErrorCode.INVALID_EMAIL);
            }
            if (!rawPassword.matches(ValidationPatterns.PASSWORD_PATTERN)) {
                throw new ApiException(ApiErrorCode.WEAK_PASSWORD, "Senha fraca");
            }

            Actor actor = resolveActorOrNull(accountId);

            final int requestedCount = (requestedPermissions == null) ? 0 : requestedPermissions.size();
            final String attemptDetails = "{"
                    + "\"scope\":\"TENANT\""
                    + ",\"requestedPermissionsCount\":" + requestedCount
                    + "}";

            TenantUser saved = auditAttemptSuccessFail(
                    SecurityAuditActionType.USER_CREATED,
                    actor,
                    normEmail,
                    null,
                    accountId,
                    null,
                    attemptDetails,
                    null,
                    () -> {

                        boolean exists = tenantUserRepository.existsByEmailAndAccountId(normEmail, accountId);
                        if (exists) throw new ApiException(ApiErrorCode.EMAIL_ALREADY_IN_USE, "Email já cadastrado nesta conta");

                        TenantUser user = new TenantUser();
                        user.setAccountId(accountId);

                        user.rename(name);
                        user.changeEmail(normEmail);

                        user.setPassword(passwordEncoder.encode(rawPassword));
                        user.setRole(role);

                        user.setOrigin(origin == null ? EntityOrigin.ADMIN : origin);

                        user.setPhone(phone);
                        user.setAvatarUrl(avatarUrl);
                        user.setLocale(locale);
                        user.setTimezone(timezone);

                        user.setMustChangePassword(mustChangePassword != null && mustChangePassword);

                        Set<TenantPermission> base = new LinkedHashSet<>(TenantRolePermissions.permissionsFor(role));
                        Set<TenantPermission> desired = new LinkedHashSet<>();
                        if (requestedPermissions != null && !requestedPermissions.isEmpty()) desired.addAll(requestedPermissions);
                        desired = PermissionScopeValidator.validateTenantPermissionsStrict(desired);

                        Set<TenantPermission> finalPerms = new LinkedHashSet<>(base);
                        finalPerms.addAll(desired);

                        user.setPermissions(finalPerms);

                        if (!StringUtils.hasText(user.getLocale())) user.setLocale("pt_BR");
                        if (!StringUtils.hasText(user.getTimezone())) user.setTimezone("America/Sao_Paulo");

                        return tenantUserRepository.save(user);
                    }
            );

            return saved;
        });
    }

    // =========================================================
    // STATUS (SUSPEND/RESTORE)
    // =========================================================

    public TenantUser setTenantUserSuspendedByAdmin(Long userId, boolean suspended) {
        Long accountId = securityUtils.getCurrentAccountId();
        return setSuspendedByAdmin(accountId, userId, suspended);
    }

    public TenantUser setTenantUserSuspendedByAccount(Long userId, boolean suspended) {
        Long accountId = securityUtils.getCurrentAccountId();
        return setSuspendedByAccount(accountId, userId, suspended);
    }

    public TenantUser setSuspendedByAdmin(Long accountId, Long userId, boolean suspended) {
        return setSuspension(accountId, userId, suspended, true);
    }

    public TenantUser setSuspendedByAccount(Long accountId, Long userId, boolean suspended) {
        return setSuspension(accountId, userId, suspended, false);
    }

    private TenantUser setSuspension(Long accountId, Long userId, boolean suspended, boolean byAdmin) {
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório");
        if (userId == null) throw new ApiException(ApiErrorCode.USER_REQUIRED);

        return transactionExecutor.inTenantTx(() -> {
            Actor actor = resolveActorOrNull(accountId);

            TenantUser user = tenantUserRepository.findByIdAndAccountIdAndDeletedFalse(userId, accountId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário não encontrado"));

            requireNotBuiltInForMutation(user);

            if (suspended && isActiveOwner(user)) {
                requireWillStillHaveAtLeastOneActiveOwner(accountId, user.getId(),
                        "Não é permitido suspender o último TENANT_OWNER ativo");
            }

            SecurityAuditActionType action = suspended ? SecurityAuditActionType.USER_SUSPENDED : SecurityAuditActionType.USER_RESTORED;

            String reason = byAdmin ? "suspendedByAdmin" : "suspendedByAccount";
            String details = "{\"scope\":\"TENANT\",\"reason\":\"" + reason + "\",\"suspended\":" + suspended + "}";

            auditAttemptSuccessFail(
                    action,
                    actor,
                    user.getEmail(),
                    user.getId(),
                    accountId,
                    null,
                    details,
                    details,
                    () -> {
                        if (byAdmin) user.setSuspendedByAdmin(suspended);
                        else user.setSuspendedByAccount(suspended);

                        tenantUserRepository.save(user);
                        return null;
                    }
            );

            return user;
        });
    }

    // =========================================================
    // PASSWORD
    // =========================================================

    public void changeMyPassword(
            Long userId,
            Long accountId,
            String currentPassword,
            String newPassword,
            String confirmNewPassword
    ) {
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório");
        if (userId == null) throw new ApiException(ApiErrorCode.USER_REQUIRED);

        if (!StringUtils.hasText(currentPassword)) throw new ApiException(ApiErrorCode.CURRENT_PASSWORD_REQUIRED, "Senha atual é obrigatória");
        if (!StringUtils.hasText(newPassword)) throw new ApiException(ApiErrorCode.NEW_PASSWORD_REQUIRED, "Nova senha é obrigatória");
        if (!StringUtils.hasText(confirmNewPassword)) throw new ApiException(ApiErrorCode.CONFIRM_PASSWORD_REQUIRED, "Confirmar nova senha é obrigatório");

        if (!newPassword.equals(confirmNewPassword)) throw new ApiException(ApiErrorCode.PASSWORD_MISMATCH, "Nova senha e confirmação não conferem");
        if (!newPassword.matches(ValidationPatterns.PASSWORD_PATTERN)) throw new ApiException(ApiErrorCode.WEAK_PASSWORD, "Senha fraca");

        transactionExecutor.inTenantTx(() -> {
            TenantUser user = tenantUserRepository.findByIdAndAccountIdAndDeletedFalse(userId, accountId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário não encontrado"));

            Actor actor = resolveActorOrNull(accountId);

            auditAttemptSuccessFail(
                    SecurityAuditActionType.PASSWORD_CHANGED,
                    actor,
                    user.getEmail(),
                    user.getId(),
                    accountId,
                    null,
                    "{\"scope\":\"TENANT\"}",
                    "{\"scope\":\"TENANT\"}",
                    () -> {
                        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
                            throw new ApiException(ApiErrorCode.CURRENT_PASSWORD_INVALID, "Senha atual inválida");
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

    public TenantUser resetPassword(Long userId, Long accountId, String newPassword) {
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório");
        if (userId == null) throw new ApiException(ApiErrorCode.USER_REQUIRED);
        if (!StringUtils.hasText(newPassword)) throw new ApiException(ApiErrorCode.INVALID_PASSWORD, "Nova senha é obrigatória");
        if (!newPassword.matches(ValidationPatterns.PASSWORD_PATTERN)) throw new ApiException(ApiErrorCode.WEAK_PASSWORD, "Senha fraca");

        return transactionExecutor.inTenantTx(() -> {
            Actor actor = resolveActorOrNull(accountId);

            TenantUser user = tenantUserRepository.findByIdAndAccountIdAndDeletedFalse(userId, accountId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário não encontrado"));

            requireNotBuiltInForMutation(user);

            auditAttemptSuccessFail(
                    SecurityAuditActionType.PASSWORD_RESET_BY_ADMIN,
                    actor,
                    user.getEmail(),
                    user.getId(),
                    accountId,
                    null,
                    "{\"scope\":\"TENANT\"}",
                    "{\"scope\":\"TENANT\"}",
                    () -> {
                        Instant now = appClock.instant();

                        user.setPassword(passwordEncoder.encode(newPassword));
                        user.setMustChangePassword(true);
                        user.setPasswordChangedAt(now);

                        user.setPasswordResetToken(null);
                        user.setPasswordResetExpires(null);

                        return tenantUserRepository.save(user);
                    }
            );

            return user;
        });
    }

    // =========================================================
    // DELETE / RESTORE
    // =========================================================

    public void softDelete(Long userId, Long accountId) {
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório");
        if (userId == null) throw new ApiException(ApiErrorCode.USER_REQUIRED);

        transactionExecutor.inTenantTx(() -> {
            Actor actor = resolveActorOrNull(accountId);

            TenantUser user = tenantUserRepository.findByIdAndAccountIdAndDeletedFalse(userId, accountId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário não encontrado"));

            requireNotBuiltInForMutation(user);

            if (isActiveOwner(user)) {
                requireWillStillHaveAtLeastOneActiveOwner(accountId, user.getId(),
                        "Não é permitido excluir o último TENANT_OWNER ativo");
            }

            auditAttemptSuccessFail(
                    SecurityAuditActionType.USER_DELETED,
                    actor,
                    user.getEmail(),
                    user.getId(),
                    accountId,
                    null,
                    "{\"scope\":\"TENANT\"}",
                    "{\"scope\":\"TENANT\"}",
                    () -> {
                        user.softDelete(appClock.instant());
                        tenantUserRepository.save(user);
                        return null;
                    }
            );

            return null;
        });
    }

    public TenantUser restore(Long userId, Long accountId) {
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório");
        if (userId == null) throw new ApiException(ApiErrorCode.USER_REQUIRED);

        return transactionExecutor.inTenantTx(() -> {
            Actor actor = resolveActorOrNull(accountId);

            TenantUser user = tenantUserRepository.findIncludingDeletedByIdAndAccountId(userId, accountId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário não encontrado"));

            return auditAttemptSuccessFail(
                    SecurityAuditActionType.USER_RESTORED,
                    actor,
                    user.getEmail(),
                    user.getId(),
                    accountId,
                    null,
                    "{\"scope\":\"TENANT\"}",
                    "{\"scope\":\"TENANT\"}",
                    () -> {
                        user.restore();
                        return tenantUserRepository.save(user);
                    }
            );
        });
    }

    public void hardDelete(Long userId, Long accountId) {
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório");
        if (userId == null) throw new ApiException(ApiErrorCode.USER_REQUIRED);

        transactionExecutor.inTenantTx(() -> {
            TenantUser user = tenantUserRepository.findIncludingDeletedByIdAndAccountId(userId, accountId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário não encontrado"));

            requireNotBuiltInForMutation(user);

            if (!user.isDeleted() && isActiveOwner(user)) {
                requireWillStillHaveAtLeastOneActiveOwner(accountId, user.getId(),
                        "Não é permitido excluir o último TENANT_OWNER ativo");
            }

            tenantUserRepository.delete(user);

            return null;
        });
    }

    // =========================================================
    // Helpers (imutabilidade / owners)
    // =========================================================

    private void requireNotBuiltInForMutation(TenantUser user) {
        if (user != null && user.isBuiltIn()) {
            throw new ApiException(USER_BUILT_IN_IMMUTABLE, "Usuário built-in não pode ser alterado");
        }
    }

    private boolean isActiveOwner(TenantUser user) {
        if (user == null) return false;
        if (user.isDeleted()) return false;
        if (user.isSuspendedByAccount()) return false;
        if (user.isSuspendedByAdmin()) return false;
        return user.getRole() != null && user.getRole().isTenantOwner();
    }

    private void requireWillStillHaveAtLeastOneActiveOwner(Long accountId, Long removingUserId, String message) {
        long owners = tenantUserRepository.countActiveOwnersByAccountId(accountId, TenantRole.TENANT_OWNER);
        if (owners <= 1) throw new ApiException(TENANT_OWNER_REQUIRED, message);
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
            String attemptDetailsJson,
            String successDetailsJson,
            AuditCallable<T> block
    ) {
        recordAudit(actionType, AuditOutcome.ATTEMPT, actor, targetEmail, targetUserId, accountId, tenantSchema, attemptDetailsJson);

        try {
            T result = block.call();
            recordAudit(actionType, AuditOutcome.SUCCESS, actor, targetEmail, targetUserId, accountId, tenantSchema, successDetailsJson);
            return result;
        } catch (ApiException ex) {
            recordAudit(actionType, outcomeFrom(ex), actor, targetEmail, targetUserId, accountId, tenantSchema, failureDetailsJson("TENANT", ex));
            throw ex;
        } catch (Exception ex) {
            recordAudit(actionType, AuditOutcome.FAILURE, actor, targetEmail, targetUserId, accountId, tenantSchema, unexpectedFailureDetailsJson("TENANT", ex));
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
            String detailsJson
    ) {
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
    }

    private Actor resolveActorOrNull(Long accountId) {
        try {
            Long actorUserId = securityUtils.getCurrentUserId();
            Long actorAccountId = securityUtils.getCurrentAccountId();

            if (actorUserId == null || actorAccountId == null) return Actor.anonymous();
            if (!actorAccountId.equals(accountId)) return new Actor(actorUserId, null);

            String actorEmail = transactionExecutor.inTenantReadOnlyTx(() ->
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

    private static String failureDetailsJson(String scope, ApiException ex) {
        String code = ex == null ? null : ex.getError();
        int status = ex == null ? 0 : ex.getStatus();
        String msg = ex == null ? null : ex.getMessage();
        return "{"
                + "\"scope\":\"" + jsonEscape(scope) + "\""
                + ",\"error\":\"" + jsonEscape(code) + "\""
                + ",\"status\":" + status
                + ",\"message\":\"" + jsonEscape(msg) + "\""
                + "}";
    }

    private static String unexpectedFailureDetailsJson(String scope, Exception ex) {
        String msg = ex == null ? null : ex.getMessage();
        return "{"
                + "\"scope\":\"" + jsonEscape(scope) + "\""
                + ",\"error\":\"UNEXPECTED\""
                + ",\"message\":\"" + jsonEscape(msg) + "\""
                + "}";
    }

    private static String jsonEscape(String v) {
        if (v == null) return "";
        return v.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record Actor(Long userId, String email) {
        static Actor anonymous() {
            return new Actor(null, null);
        }
    }
}
