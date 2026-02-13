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

    private static final String BUILT_IN_USER_IMMUTABLE = "BUILT_IN_USER_IMMUTABLE";
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

            if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
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

            Actor actor = resolveActorOrNull(accountId);

            final int requestedCount = (requestedPermissions == null) ? 0 : requestedPermissions.size();
            final String attemptDetails = "{"
                    + "\"scope\":\"TENANT\""
                    + ",\"role\":\"" + jsonEscape(role.name()) + "\""
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

                        return tenantUserRepository.save(user);
                    }
            );

            recordAudit(
                    SecurityAuditActionType.USER_CREATED,
                    AuditOutcome.SUCCESS,
                    actor,
                    saved.getEmail(),
                    saved.getId(),
                    accountId,
                    null,
                    "{"
                            + "\"scope\":\"TENANT\""
                            + ",\"role\":\"" + jsonEscape(role.name()) + "\""
                            + "}"
            );

            Set<TenantPermission> base = new LinkedHashSet<>(TenantRolePermissions.permissionsFor(role));
            Set<TenantPermission> desired = new LinkedHashSet<>();
            if (requestedPermissions != null && !requestedPermissions.isEmpty()) desired.addAll(requestedPermissions);
            desired = PermissionScopeValidator.validateTenantPermissionsStrict(desired);

            Set<TenantPermission> finalPerms = new LinkedHashSet<>(base);
            finalPerms.addAll(desired);

            recordAudit(
                    SecurityAuditActionType.PERMISSIONS_CHANGED,
                    AuditOutcome.SUCCESS,
                    actor,
                    saved.getEmail(),
                    saved.getId(),
                    accountId,
                    null,
                    "{"
                            + "\"scope\":\"TENANT\""
                            + ",\"reason\":\"create\""
                            + ",\"baseCount\":" + base.size()
                            + ",\"requestedCount\":" + requestedCount
                            + ",\"finalCount\":" + finalPerms.size()
                            + "}"
            );

            return saved;
        });
    }

    // =========================================================
    // UPDATE: STATUS / PROFILE
    // =========================================================

    public void setSuspendedByAdmin(Long accountId, Long userId, boolean suspended) {
        setSuspension(accountId, userId, suspended, true);
    }

    public void setSuspendedByAccount(Long accountId, Long userId, boolean suspended) {
        setSuspension(accountId, userId, suspended, false);
    }

    private void setSuspension(Long accountId, Long userId, boolean suspended, boolean byAdmin) {
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        if (userId == null) throw new ApiException(ApiErrorCode.USER_REQUIRED, "userId é obrigatório", 400);

        transactionExecutor.inTenantTx(() -> {
            Actor actor = resolveActorOrNull(accountId);

            TenantUser user = tenantUserRepository.findByIdAndAccountIdAndDeletedFalse(userId, accountId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário não encontrado", 404));

            requireNotBuiltInForMutation(user, "Não é permitido suspender usuário BUILT_IN");

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
            String name,
            String phone,
            String avatarUrl,
            String locale,
            String timezone,
            Instant now // mantido por compat
    ) {
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        if (userId == null) throw new ApiException(ApiErrorCode.USER_REQUIRED, "userId é obrigatório", 400);

        return transactionExecutor.inTenantTx(() -> {
            Actor actor = resolveActorOrNull(accountId);

            TenantUser user = tenantUserRepository.findByIdAndAccountIdAndDeletedFalse(userId, accountId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário não encontrado", 404));

            requireNotBuiltInForMutation(user, "Não é permitido alterar perfil de usuário BUILT_IN");

            return auditAttemptSuccessFail(
                    SecurityAuditActionType.USER_UPDATED,
                    actor,
                    user.getEmail(),
                    user.getId(),
                    accountId,
                    null,
                    "{\"scope\":\"TENANT\",\"reason\":\"updateProfile\"}",
                    null,
                    () -> {
                        boolean changed = false;

                        if (StringUtils.hasText(name)) { user.rename(name); changed = true; }
                        if (StringUtils.hasText(phone)) { user.setPhone(phone.trim()); changed = true; }
                        if (StringUtils.hasText(locale)) { user.setLocale(locale.trim()); changed = true; }
                        if (StringUtils.hasText(timezone)) { user.setTimezone(timezone.trim()); changed = true; }

                        if (avatarUrl != null) {
                            String trimmed = avatarUrl.trim();
                            user.setAvatarUrl(trimmed.isEmpty() ? null : trimmed);
                            changed = true;
                        }

                        TenantUser saved = tenantUserRepository.save(user);

                        recordAudit(
                                SecurityAuditActionType.USER_UPDATED,
                                AuditOutcome.SUCCESS,
                                actor,
                                saved.getEmail(),
                                saved.getId(),
                                accountId,
                                null,
                                "{\"scope\":\"TENANT\",\"reason\":\"updateProfile\",\"changed\":" + changed + "}"
                        );

                        return saved;
                    }
            );
        });
    }

    // =========================================================
    // PASSWORD
    // =========================================================

    public TenantUser resetPassword(Long userId, Long accountId, String newPassword) {
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        if (userId == null) throw new ApiException(ApiErrorCode.USER_REQUIRED, "userId é obrigatório", 400);
        if (!StringUtils.hasText(newPassword)) throw new ApiException(ApiErrorCode.INVALID_PASSWORD, "Senha é obrigatória", 400);

        if (!newPassword.matches(ValidationPatterns.PASSWORD_PATTERN)) {
            throw new ApiException(ApiErrorCode.WEAK_PASSWORD, "Senha fraca", 400);
        }

        return transactionExecutor.inTenantTx(() -> {
            TenantUser user = tenantUserRepository.findIncludingDeletedByIdAndAccountId(userId, accountId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário não encontrado", 404));

            Instant now = appClock.instant();

            user.setPassword(passwordEncoder.encode(newPassword));
            user.setMustChangePassword(false);
            user.setPasswordChangedAt(now);
            user.setPasswordResetToken(null);
            user.setPasswordResetExpires(null);

            return tenantUserRepository.save(user);
        });
    }

    public void resetPasswordWithToken(Long accountId, String email, String token, String newPassword) {
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        if (!StringUtils.hasText(token)) throw new ApiException(ApiErrorCode.TOKEN_REQUIRED, "token é obrigatório", 400);
        if (!StringUtils.hasText(newPassword)) throw new ApiException(ApiErrorCode.INVALID_PASSWORD, "Senha é obrigatória", 400);

        if (!newPassword.matches(ValidationPatterns.PASSWORD_PATTERN)) {
            throw new ApiException(ApiErrorCode.WEAK_PASSWORD, "Senha fraca", 400);
        }

        transactionExecutor.inTenantTx(() -> {
            TenantUser user = tenantUserRepository.findByPasswordResetTokenAndAccountId(token, accountId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.TOKEN_INVALID, "Token inválido", 400));

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
        });
    }

    public void changeMyPassword(Long userId, Long accountId, String currentPassword, String newPassword, String confirmNewPassword) {
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        if (userId == null) throw new ApiException(ApiErrorCode.USER_REQUIRED, "userId é obrigatório", 400);

        if (!StringUtils.hasText(currentPassword)) throw new ApiException(ApiErrorCode.CURRENT_PASSWORD_REQUIRED, "Senha atual é obrigatória", 400);
        if (!StringUtils.hasText(newPassword)) throw new ApiException(ApiErrorCode.NEW_PASSWORD_REQUIRED, "Nova senha é obrigatória", 400);
        if (!StringUtils.hasText(confirmNewPassword)) throw new ApiException(ApiErrorCode.CONFIRM_PASSWORD_REQUIRED, "Confirmar nova senha é obrigatório", 400);

        if (!newPassword.equals(confirmNewPassword)) throw new ApiException(ApiErrorCode.PASSWORD_MISMATCH, "Nova senha e confirmação não conferem", 400);
        if (!newPassword.matches(ValidationPatterns.PASSWORD_PATTERN)) throw new ApiException(ApiErrorCode.WEAK_PASSWORD, "Senha fraca", 400);

        transactionExecutor.inTenantTx(() -> {
            TenantUser user = tenantUserRepository.findByIdAndAccountIdAndDeletedFalse(userId, accountId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário não encontrado", 404));

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

    public void softDelete(Long userId, Long accountId) {
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        if (userId == null) throw new ApiException(ApiErrorCode.USER_REQUIRED, "userId é obrigatório", 400);

        transactionExecutor.inTenantTx(() -> {
            Actor actor = resolveActorOrNull(accountId);

            TenantUser user = tenantUserRepository.findIncludingDeletedByIdAndAccountId(userId, accountId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário não encontrado", 404));

            if (user.isDeleted()) return null;

            requireNotBuiltInForMutation(user, "Não é permitido excluir usuário BUILT_IN");

            if (isActiveOwner(user)) {
                requireWillStillHaveAtLeastOneActiveOwner(accountId, user.getId(),
                        "Não é permitido excluir o último TENANT_OWNER ativo");
            }

            auditAttemptSuccessFail(
                    SecurityAuditActionType.USER_SUSPENDED,
                    actor,
                    user.getEmail(),
                    user.getId(),
                    accountId,
                    null,
                    "{\"scope\":\"TENANT\",\"reason\":\"softDelete\"}",
                    "{\"scope\":\"TENANT\",\"reason\":\"softDelete\"}",
                    () -> {
                        Instant now = appClock.instant();
                        user.softDelete(now, appClock.epochMillis());
                        tenantUserRepository.save(user);
                        return null;
                    }
            );

            return null;
        });
    }

    public TenantUser restore(Long userId, Long accountId) {
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        if (userId == null) throw new ApiException(ApiErrorCode.USER_REQUIRED, "userId é obrigatório", 400);

        return transactionExecutor.inTenantTx(() -> {
            Actor actor = resolveActorOrNull(accountId);

            TenantUser user = tenantUserRepository.findIncludingDeletedByIdAndAccountId(userId, accountId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário não encontrado", 404));

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
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        if (userId == null) throw new ApiException(ApiErrorCode.USER_REQUIRED, "userId é obrigatório", 400);

        transactionExecutor.inTenantTx(() -> {
            TenantUser user = tenantUserRepository.findIncludingDeletedByIdAndAccountId(userId, accountId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário não encontrado", 404));

            requireNotBuiltInForMutation(user, "Não é permitido hard-delete de usuário BUILT_IN");

            if (!user.isDeleted() && isActiveOwner(user)) {
                requireWillStillHaveAtLeastOneActiveOwner(accountId, user.getId(),
                        "Não é permitido excluir o último TENANT_OWNER ativo");
            }

            tenantUserRepository.delete(user);
            return null;
        });
    }

    public TenantUser save(TenantUser user) {
        if (user == null) throw new ApiException(ApiErrorCode.INVALID_REQUEST, "Usuário inválido", 400);
        return transactionExecutor.inTenantTx(() -> tenantUserRepository.save(user));
    }

    // =========================================================
    // ROLE TRANSFER (OWNER)
    // =========================================================

    public void transferTenantOwnerRole(Long accountId, Long fromUserId, Long toUserId) {
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        if (fromUserId == null) throw new ApiException(ApiErrorCode.FROM_USER_REQUIRED, "fromUserId é obrigatório", 400);
        if (toUserId == null) throw new ApiException(ApiErrorCode.TO_USER_REQUIRED, "toUserId é obrigatório", 400);
        if (fromUserId.equals(toUserId)) throw new ApiException(ApiErrorCode.INVALID_TRANSFER, "Não é possível transferir para si mesmo", 400);

        transactionExecutor.inTenantTx(() -> {
            Actor actor = resolveActorOrNull(accountId);

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

            securityAuditService.record(
                    SecurityAuditActionType.ROLE_CHANGED,
                    AuditOutcome.ATTEMPT,
                    actor.email(),
                    actor.userId(),
                    from.getEmail(),
                    from.getId(),
                    accountId,
                    null,
                    "{\"scope\":\"TENANT\",\"kind\":\"transferOwner\",\"side\":\"from\"}"
            );
            securityAuditService.record(
                    SecurityAuditActionType.ROLE_CHANGED,
                    AuditOutcome.ATTEMPT,
                    actor.email(),
                    actor.userId(),
                    to.getEmail(),
                    to.getId(),
                    accountId,
                    null,
                    "{\"scope\":\"TENANT\",\"kind\":\"transferOwner\",\"side\":\"to\"}"
            );

            try {
                from.setRole(TenantRole.TENANT_ADMIN);
                to.setRole(TenantRole.TENANT_OWNER);

                from.setPermissions(new LinkedHashSet<>(TenantRolePermissions.permissionsFor(from.getRole())));
                to.setPermissions(new LinkedHashSet<>(TenantRolePermissions.permissionsFor(to.getRole())));

                tenantUserRepository.save(from);
                tenantUserRepository.save(to);

                securityAuditService.record(
                        SecurityAuditActionType.ROLE_CHANGED,
                        AuditOutcome.SUCCESS,
                        actor.email(),
                        actor.userId(),
                        from.getEmail(),
                        from.getId(),
                        accountId,
                        null,
                        "{"
                                + "\"scope\":\"TENANT\""
                                + ",\"kind\":\"transferOwner\""
                                + ",\"side\":\"from\""
                                + ",\"from\":\"" + jsonEscape(beforeFrom == null ? null : beforeFrom.name()) + "\""
                                + ",\"to\":\"" + jsonEscape(from.getRole() == null ? null : from.getRole().name()) + "\""
                                + "}"
                );
                securityAuditService.record(
                        SecurityAuditActionType.ROLE_CHANGED,
                        AuditOutcome.SUCCESS,
                        actor.email(),
                        actor.userId(),
                        to.getEmail(),
                        to.getId(),
                        accountId,
                        null,
                        "{"
                                + "\"scope\":\"TENANT\""
                                + ",\"kind\":\"transferOwner\""
                                + ",\"side\":\"to\""
                                + ",\"from\":\"" + jsonEscape(beforeTo == null ? null : beforeTo.name()) + "\""
                                + ",\"to\":\"" + jsonEscape(to.getRole() == null ? null : to.getRole().name()) + "\""
                                + "}"
                );

                securityAuditService.record(
                        SecurityAuditActionType.PERMISSIONS_CHANGED,
                        AuditOutcome.SUCCESS,
                        actor.email(),
                        actor.userId(),
                        from.getEmail(),
                        from.getId(),
                        accountId,
                        null,
                        "{"
                                + "\"scope\":\"TENANT\""
                                + ",\"reason\":\"transferOwner\""
                                + ",\"side\":\"from\""
                                + ",\"finalCount\":" + (from.getPermissions() == null ? 0 : from.getPermissions().size())
                                + "}"
                );
                securityAuditService.record(
                        SecurityAuditActionType.PERMISSIONS_CHANGED,
                        AuditOutcome.SUCCESS,
                        actor.email(),
                        actor.userId(),
                        to.getEmail(),
                        to.getId(),
                        accountId,
                        null,
                        "{"
                                + "\"scope\":\"TENANT\""
                                + ",\"reason\":\"transferOwner\""
                                + ",\"side\":\"to\""
                                + ",\"finalCount\":" + (to.getPermissions() == null ? 0 : to.getPermissions().size())
                                + "}"
                );

                return null;
            } catch (ApiException ex) {
                securityAuditService.record(
                        SecurityAuditActionType.ROLE_CHANGED,
                        outcomeFrom(ex),
                        actor.email(),
                        actor.userId(),
                        from.getEmail(),
                        from.getId(),
                        accountId,
                        null,
                        failureDetailsJson("TENANT", ex)
                );
                throw ex;
            } catch (Exception ex) {
                securityAuditService.record(
                        SecurityAuditActionType.ROLE_CHANGED,
                        AuditOutcome.FAILURE,
                        actor.email(),
                        actor.userId(),
                        from.getEmail(),
                        from.getId(),
                        accountId,
                        null,
                        unexpectedFailureDetailsJson("TENANT", ex)
                );
                throw ex;
            }
        });
    }

    // =========================================================
    // HELPERS / GUARDS
    // =========================================================

    private void requireNotBuiltInForMutation(TenantUser user, String message) {
        if (user != null && user.getOrigin() == EntityOrigin.BUILT_IN) {
            throw new ApiException(BUILT_IN_USER_IMMUTABLE, message, 403);
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
        if (owners <= 1) throw new ApiException(TENANT_OWNER_REQUIRED, message, 409);
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
        String type = ex == null ? null : ex.getClass().getSimpleName();
        String msg = ex == null ? null : ex.getMessage();
        return "{"
                + "\"scope\":\"" + jsonEscape(scope) + "\""
                + ",\"unexpected\":\"" + jsonEscape(type) + "\""
                + ",\"message\":\"" + jsonEscape(msg) + "\""
                + "}";
    }

    private static String jsonEscape(String s) {
        if (s == null) return "";
        return s
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ")
                .replace("\t", " ")
                .trim();
    }

    private record Actor(Long userId, String email) {
        static Actor anonymous() { return new Actor(null, null); }
    }
}
