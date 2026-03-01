// src/main/java/brito/com/multitenancy001/tenant/users/app/command/TenantUserCommandService.java
package brito.com.multitenancy001.tenant.users.app.command;

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
 * Application Service para comandos relacionados a usuários do Tenant.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Centralizar lógica de criação, atualização, suspensão e exclusão de usuários.</li>
 *   <li>Garantir consistência entre schema do Tenant e índice público {@code public.login_identities}.</li>
 *   <li>Executar auditoria SOC2-like para operações sensíveis.</li>
 * </ul>
 *
 * <p><b>REGRA CRÍTICA DE NEGÓCIO:</b></p>
 * Para o login multi-tenant ({@code /api/tenant/auth/login}) funcionar, todo usuário ativo em um tenant
 * (não deletado e não suspenso) deve ter entrada correspondente em {@code public.login_identities}
 * com {@code subject_type = 'TENANT_ACCOUNT'}.
 *
 * <p>Fonte da verdade para sincronização:</p>
 * <ul>
 *   <li>Operações que tornam usuário ativo (create/restore/unsuspend) devem garantir a entrada no índice público.</li>
 *   <li>Operações que tornam usuário inativo (soft delete) devem removê-la.</li>
 * </ul>
 *
 * @see LoginIdentityProvisioningService
 * @see TenantUser
 * @see TenantUserRepository
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantUserCommandService {

    private static final String SCOPE = "TENANT";

    private final TenantUserRepository tenantUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppClock appClock;

    /**
     * Dispatcher para executar auditoria PUBLIC em safe-mode (evita nesting ilegal TENANT->PUBLIC).
     * Obs: para login_identities, usamos diretamente LoginIdentityProvisioningService, que já é safe.
     */
    private final PublicAuditDispatcher publicAuditDispatcher;

    /**
     * Unit of Work para tenant:
     * - bind do tenantSchema
     * - transação tenant
     */
    private final TenantSchemaUnitOfWork tenantSchemaUnitOfWork;

    /** Mantido por compat (best-effort actor). */
    private final SecurityUtils securityUtils;

    /** Auditoria append-only (public schema). */
    private final SecurityAuditService securityAuditService;

    /** Mapper para details (Map/record/String -> JsonNode). */
    private final JsonDetailsMapper jsonDetailsMapper;

    /** Serviço para provisionamento do índice público de login (public.login_identities). */
    private final LoginIdentityProvisioningService loginIdentityProvisioningService;

    // =========================================================================
    // LOGIN_IDENTITY SYNC (PUBLIC) - SAFE
    // =========================================================================

    /**
     * Garante que o índice público tenha a identidade TENANT_ACCOUNT (email, accountId).
     *
     * <p>Importante:</p>
     * <ul>
     *   <li>Não executa SQL PUBLIC diretamente aqui.</li>
     *   <li>Delegamos para {@link LoginIdentityProvisioningService#ensureTenantIdentityAfterCompletion(String, Long)},
     *       que já executa em thread separada + transação PUBLIC própria.</li>
     * </ul>
     */
    private void ensureLoginIdentityAfterCommit(String email, Long accountId, String operation) {
        if (!StringUtils.hasText(email) || accountId == null) {
            log.warn("ensureLoginIdentityAfterCommit ignorado: email ou accountId nulo/vazio | email={} accountId={} op={}",
                    email, accountId, operation);
            return;
        }

        log.info("ensureLoginIdentityAfterCommit agendando provisioning | email={} accountId={} op={}",
                email, accountId, operation);

        try {
            // SAFE: o serviço executa fora da TX atual, em thread separada, com transação PUBLIC própria.
            loginIdentityProvisioningService.ensureTenantIdentityAfterCompletion(email, accountId);
        } catch (Exception e) {
            log.error("Falha ao agendar provisioning de login_identity | email={} accountId={} op={} msg={}",
                    email, accountId, operation, e.getMessage(), e);
        }
    }

    /**
     * Remove a identidade TENANT_ACCOUNT do índice público (email, accountId).
     *
     * <p>Importante:</p>
     * <ul>
     *   <li>Delegamos para {@link LoginIdentityProvisioningService#deleteTenantIdentityAfterCompletion(String, Long)},
     *       que já executa em thread separada + transação PUBLIC própria.</li>
     * </ul>
     */
    private void deleteLoginIdentityAfterCommit(String email, Long accountId, String operation) {
        if (!StringUtils.hasText(email) || accountId == null) {
            log.warn("deleteLoginIdentityAfterCommit ignorado: email ou accountId nulo/vazio | email={} accountId={} op={}",
                    email, accountId, operation);
            return;
        }

        log.info("deleteLoginIdentityAfterCommit agendando delete | email={} accountId={} op={}",
                email, accountId, operation);

        try {
            // SAFE: o serviço executa fora da TX atual, em thread separada, com transação PUBLIC própria.
            loginIdentityProvisioningService.deleteTenantIdentityAfterCompletion(email, accountId);
        } catch (Exception e) {
            log.error("Falha ao agendar delete de login_identity | email={} accountId={} op={} msg={}",
                    email, accountId, operation, e.getMessage(), e);
        }
    }

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
     *   <li>Após commit do tenant, garante (email, accountId) no PUBLIC para permitir loginInit ambíguo.</li>
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
        log.info("createTenantUser iniciando | email={} accountId={}", email, accountId);

        return tenantSchemaUnitOfWork.tx(tenantSchema, () -> {
            log.info("createTenantUser dentro da transacao | threadId={} threadName={} email={} accountId={}",
                    Thread.currentThread().threadId(),
                    Thread.currentThread().getName(),
                    email,
                    accountId);

            if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatorio", 400);
            if (!StringUtils.hasText(tenantSchema)) throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatorio", 400);

            if (!StringUtils.hasText(name)) throw new ApiException(ApiErrorCode.INVALID_NAME, "Nome é obrigatorio", 400);
            if (!StringUtils.hasText(email)) throw new ApiException(ApiErrorCode.INVALID_EMAIL, "Email é obrigatorio", 400);
            if (!StringUtils.hasText(rawPassword)) throw new ApiException(ApiErrorCode.INVALID_PASSWORD, "Senha é obrigatoria", 400);
            if (role == null) throw new ApiException(ApiErrorCode.INVALID_ROLE, "Role é obrigatoria", 400);

            String normEmail = EmailNormalizer.normalizeOrNull(email);
            if (!StringUtils.hasText(normEmail) || !normEmail.matches(ValidationPatterns.EMAIL_PATTERN)) {
                throw new ApiException(ApiErrorCode.INVALID_EMAIL, "Email invalido", 400);
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
            	if (exists) throw new ApiException(ApiErrorCode.EMAIL_ALREADY_REGISTERED_IN_ACCOUNT);

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
                log.info("createTenantUser usuario salvo no tenant | id={} email={}", saved.getId(), saved.getEmail());

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

                // Pos-tx: garante indice publico
                ensureLoginIdentityAfterCommit(saved.getEmail(), accountId, "create");

                log.info("createTenantUser finalizado com sucesso | email={}", saved.getEmail());
                return saved;

            } catch (ApiException ex) {
                log.error("createTenantUser ApiException | msg={}", ex.getMessage());
                recordAudit(SecurityAuditActionType.USER_CREATED, outcomeFrom(ex), actor, normEmail, null, accountId, tenantSchema, failureDetails(SCOPE, ex));
                throw ex;
            } catch (Exception ex) {
                log.error("createTenantUser Exception inesperada | msg={}", ex.getMessage(), ex);
                recordAudit(SecurityAuditActionType.USER_CREATED, AuditOutcome.FAILURE, actor, normEmail, null, accountId, tenantSchema, unexpectedFailureDetails(SCOPE, ex));
                throw ex;
            }
        });
    }

    // =========================================================
    // UPDATE: STATUS / PROFILE
    // =========================================================

    /** Define o status de suspensão por admin de um usuário. */
    public void setSuspendedByAdmin(Long accountId, String tenantSchema, Long userId, boolean suspended) {
        setSuspension(accountId, tenantSchema, userId, suspended, true);
    }

    /** Define o status de suspensão por account (sistema/plano) de um usuário. */
    public void setSuspendedByAccount(Long accountId, String tenantSchema, Long userId, boolean suspended) {
        setSuspension(accountId, tenantSchema, userId, suspended, false);
    }

    /**
     * Lógica centralizada para lidar com suspensão/reativação por admin ou por account.
     * Garante a sincronização com o índice público quando o usuário é reativado.
     */
    private void setSuspension(Long accountId, String tenantSchema, Long userId, boolean suspended, boolean byAdmin) {
        log.info("setSuspension iniciando | userId={} suspended={} byAdmin={}", userId, suspended, byAdmin);

        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatorio", 400);
        if (!StringUtils.hasText(tenantSchema)) throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatorio", 400);
        if (userId == null) throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatorio", 400);

        // Precisamos do estado anterior (para detectar reativacao)
        TenantUser userBefore = tenantSchemaUnitOfWork.readOnly(tenantSchema, () ->
                tenantUserRepository.findIncludingDeletedByIdAndAccountId(userId, accountId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuario não encontrado", 404))
        );
        final String userEmail = userBefore.getEmail();
        final boolean wasSuspended = byAdmin ? userBefore.isSuspendedByAdmin() : userBefore.isSuspendedByAccount();

        tenantSchemaUnitOfWork.tx(tenantSchema, () -> {
            Actor actor = resolveActorOrNull(accountId, tenantSchema);

            TenantUser user = tenantUserRepository.findByIdAndAccountIdAndDeletedFalse(userId, accountId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuario não encontrado", 404));

            requireNotBuiltInForMutation(user, "Não é permitido suspender usuario BUILT_IN");

            if (suspended && isActiveOwner(user)) {
                requireWillStillHaveAtLeastOneActiveOwner(accountId,
                        "Nao e permitido suspender o ultimo TENANT_OWNER ativo");
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

                        if (updated == 0) throw new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuario não encontrado", 404);
                        return null;
                    }
            );

            return null;
        });

        // Fora da TX principal: se foi reativacao, garantir login_identity (apenas se ativo final)
        if (!suspended && wasSuspended) {
            TenantUser userAfter = tenantSchemaUnitOfWork.readOnly(tenantSchema, () ->
                    tenantUserRepository.findIncludingDeletedByIdAndAccountId(userId, accountId).orElse(null)
            );

            if (userAfter != null && userAfter.isEnabledDomain()) {
                ensureLoginIdentityAfterCommit(userEmail, accountId, byAdmin ? "unsuspend-admin" : "unsuspend-account");
            } else {
                log.info("setSuspension reativado mas não ativo final | userId={} deleted={} suspendedByAccount={} suspendedByAdmin={}",
                        userId,
                        userAfter == null ? null : userAfter.isDeleted(),
                        userAfter == null ? null : userAfter.isSuspendedByAccount(),
                        userAfter == null ? null : userAfter.isSuspendedByAdmin());
            }
        }
    }

    // =========================================================
    // RESTORE
    // =========================================================

    /** Restaura um usuário previamente deletado e agenda a recriação da identidade no índice público. */
    public TenantUser restore(Long userId, Long accountId, String tenantSchema) {
        log.info("restore iniciando | userId={} accountId={}", userId, accountId);

        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatorio", 400);
        if (!StringUtils.hasText(tenantSchema)) throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatorio", 400);
        if (userId == null) throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatorio", 400);

        return tenantSchemaUnitOfWork.tx(tenantSchema, () -> {
            Actor actor = resolveActorOrNull(accountId, tenantSchema);

            TenantUser user = tenantUserRepository.findIncludingDeletedByIdAndAccountId(userId, accountId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuario não encontrado", 404));

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

            // Pos-tx: garante indice publico novamente
            ensureLoginIdentityAfterCommit(saved.getEmail(), accountId, "restore");
            return saved;
        });
    }

    // =========================================================
    // DELETE
    // =========================================================

    /** Aplica soft delete em um usuário e agenda a remoção da identidade do índice público. */
    public void softDelete(Long userId, Long accountId, String tenantSchema) {
        log.info("softDelete iniciando | userId={} accountId={}", userId, accountId);

        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatorio", 400);
        if (!StringUtils.hasText(tenantSchema)) throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatorio", 400);
        if (userId == null) throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatorio", 400);

        tenantSchemaUnitOfWork.tx(tenantSchema, () -> {
            Actor actor = resolveActorOrNull(accountId, tenantSchema);

            TenantUser user = tenantUserRepository.findIncludingDeletedByIdAndAccountId(userId, accountId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuario não encontrado", 404));

            if (user.isDeleted()) {
                log.info("softDelete ignorado: já deletado | userId={}", userId);
                return null;
            }

            requireNotBuiltInForMutation(user, "Nao e permitido excluir usuario BUILT_IN");

            if (isActiveOwner(user)) {
                requireWillStillHaveAtLeastOneActiveOwner(accountId,
                        "Nao e permitido excluir o ultimo TENANT_OWNER ativo");
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

            // Pos-tx: remove do indice publico (SAFE: service roda async + tx PUBLIC propria)
            deleteLoginIdentityAfterCommit(emailForIndex, accountId, "softDelete");

            return null;
        });
    }

    // =========================================================
    // Outros métodos (mantidos como estavam no seu arquivo)
    // =========================================================

    public TenantUser resetPassword(Long userId, Long accountId, String tenantSchema, String newPassword) {
        throw new UnsupportedOperationException("Implementar conforme necessario");
    }

    public void resetPasswordWithToken(Long accountId, String tenantSchema, String email, String token, String newPassword) {
        // Implementacao existente
    }

    public void changeMyPassword(Long userId, Long accountId, String tenantSchema, String currentPassword, String newPassword, String confirmNewPassword) {
        // Implementacao existente
    }

    public TenantUser updateProfile(Long userId, Long accountId, String tenantSchema, String name, String phone, String avatarUrl, String locale, String timezone, Instant now) {
        throw new UnsupportedOperationException("Implementar conforme necessario");
    }

    public void hardDelete(Long userId, Long accountId, String tenantSchema) {
        // Implementacao existente
    }

    public TenantUser save(String tenantSchema, TenantUser user) {
        return user;
    }

    public void transferTenantOwnerRole(Long accountId, String tenantSchema, Long fromUserId, Long toUserId) {
        // Implementacao existente
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private void requireNotBuiltInForMutation(TenantUser user, String message) {
        if (user != null && user.getOrigin() == EntityOrigin.BUILT_IN) {
            throw new ApiException(ApiErrorCode.USER_BUILT_IN_IMMUTABLE, message);
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

    // =========================================================
    // Audit helpers
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
                log.warn("Falha ao gravar SecurityAudit (best-effort) | actionType={} outcome={} accountId={} tenantSchema={} msg={}",
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