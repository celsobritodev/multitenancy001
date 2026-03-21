package brito.com.multitenancy001.tenant.users.app.command;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;

import brito.com.multitenancy001.infrastructure.publicschema.audit.PublicAuditDispatcher;
import brito.com.multitenancy001.infrastructure.publicschema.audit.SecurityAuditService;
import brito.com.multitenancy001.infrastructure.security.SecurityUtils;
import brito.com.multitenancy001.infrastructure.tenant.TenantSchemaUnitOfWork;
import brito.com.multitenancy001.infrastructure.tx.AfterTransactionCompletion;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;
import brito.com.multitenancy001.shared.domain.common.EntityOrigin;
import brito.com.multitenancy001.shared.domain.service.LoginIdentityService;
import brito.com.multitenancy001.shared.json.JsonDetailsMapper;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.security.PermissionScopeValidator;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.shared.validation.ValidationPatterns;
import brito.com.multitenancy001.tenant.security.TenantPermission;
import brito.com.multitenancy001.tenant.security.TenantRole;
import brito.com.multitenancy001.tenant.security.TenantRolePermissions;
import brito.com.multitenancy001.tenant.subscription.app.TenantQuotaEnforcementService;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import brito.com.multitenancy001.tenant.users.persistence.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Application Service para comandos relacionados a usuários do tenant.
 *
 * <p><b>Responsabilidades:</b></p>
 * <ul>
 *   <li>Criar, suspender, restaurar e excluir usuários do tenant.</li>
 *   <li>Garantir auditoria de segurança nas mutações relevantes.</li>
 *   <li>Executar sincronização best-effort com login identities no PUBLIC.</li>
 *   <li>Aplicar enforcement de quota no write-path canônico de criação.</li>
 * </ul>
 *
 * <p><b>Diretrizes arquiteturais:</b></p>
 * <ul>
 *   <li>O enforcement de seats ocorre neste service.</li>
 *   <li>A validação de quota deve acontecer antes da transação tenant de escrita.</li>
 *   <li>Evitar crossing PUBLIC dentro do bloco principal de save do tenant.</li>
 *   <li>Na V30, o bloqueio por hard limit deve acontecer no write-path antes do save.</li>
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

    private final PublicAuditDispatcher publicAuditDispatcher;
    private final TenantSchemaUnitOfWork tenantSchemaUnitOfWork;
    private final SecurityUtils securityUtils;
    private final SecurityAuditService securityAuditService;
    private final JsonDetailsMapper jsonDetailsMapper;
    private final AfterTransactionCompletion afterTransactionCompletion;
    private final LoginIdentityService loginIdentityService;
    private final TenantQuotaEnforcementService tenantQuotaEnforcementService;

    /**
     * Cria um usuário de tenant, aplicando permissões finais (base + requested validadas).
     *
     * <p><b>Fluxo crítico da V30:</b></p>
     * <ol>
     *   <li>Validar payload e contexto.</li>
     *   <li>Normalizar email e validar senha.</li>
     *   <li>Executar quota enforcement fora da transação tenant de escrita.</li>
     *   <li>Persistir usuário dentro da transação tenant.</li>
     *   <li>Executar sincronização de login identity após completion, em best-effort.</li>
     * </ol>
     *
     * @param accountId id da conta
     * @param tenantSchema schema tenant
     * @param name nome do usuário
     * @param email email do usuário
     * @param rawPassword senha em texto puro
     * @param role role do usuário
     * @param phone telefone
     * @param avatarUrl avatar
     * @param locale locale
     * @param timezone timezone
     * @param requestedPermissions permissões solicitadas
     * @param mustChangePassword flag de troca obrigatória de senha
     * @param origin origem da entidade
     * @return usuário salvo
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
        log.info(
                "Iniciando criação de usuário tenant (PRE-CHECK). accountId={}, tenantSchema={}, email={}, role={}",
                accountId,
                tenantSchema,
                email,
                role
        );

        validateCreateInputs(accountId, tenantSchema, name, email, rawPassword, role);

        String normalizedTenantSchema = tenantSchema.trim();
        String normalizedName = name.trim();
        String normEmail = EmailNormalizer.normalizeOrNull(email);

        if (!StringUtils.hasText(normEmail) || !normEmail.matches(ValidationPatterns.EMAIL_PATTERN)) {
            throw new ApiException(ApiErrorCode.INVALID_EMAIL, "Email invalido", 400);
        }

        if (!rawPassword.matches(ValidationPatterns.PASSWORD_PATTERN)) {
            throw new ApiException(ApiErrorCode.WEAK_PASSWORD, "Senha fraca", 400);
        }

        tenantQuotaEnforcementService.assertCanCreateUser(accountId, normalizedTenantSchema);

        AtomicReference<String> savedEmail = new AtomicReference<>();
        AtomicReference<Long> savedUserId = new AtomicReference<>();

        TenantUser saved = tenantSchemaUnitOfWork.tx(normalizedTenantSchema, () -> {
            log.debug(
                    "Criando usuário dentro da transação tenant. accountId={}, tenantSchema={}, email={}, threadId={}, threadName={}",
                    accountId,
                    normalizedTenantSchema,
                    normEmail,
                    Thread.currentThread().threadId(),
                    Thread.currentThread().getName()
            );

            final Actor actor = resolveActorOrNull(accountId, normalizedTenantSchema);
            final int requestedCount = requestedPermissions == null ? 0 : requestedPermissions.size();

            recordAudit(
                    SecurityAuditActionType.USER_CREATED,
                    AuditOutcome.ATTEMPT,
                    actor,
                    normEmail,
                    null,
                    accountId,
                    normalizedTenantSchema,
                    m(
                            "scope", SCOPE,
                            "stage", "before_save",
                            "role", role.name(),
                            "requestedPermissionsCount", requestedCount
                    )
            );

            try {
                boolean exists = tenantUserRepository.existsByEmailAndAccountId(normEmail, accountId);
                if (exists) {
                    throw new ApiException(ApiErrorCode.EMAIL_ALREADY_REGISTERED_IN_ACCOUNT);
                }

                TenantUser user = new TenantUser();
                user.setAccountId(accountId);
                user.rename(normalizedName);
                user.changeEmail(normEmail);
                user.setPassword(passwordEncoder.encode(rawPassword));
                user.setRole(role);
                user.setOrigin(origin == null ? EntityOrigin.ADMIN : origin);
                user.setMustChangePassword(Boolean.TRUE.equals(mustChangePassword));

                Instant now = appClock.instant();
                user.setPasswordChangedAt(user.isMustChangePassword() ? null : now);

                user.setPhone(StringUtils.hasText(phone) ? phone.trim() : null);
                user.setAvatarUrl(StringUtils.hasText(avatarUrl) ? avatarUrl.trim() : null);
                user.setLocale(StringUtils.hasText(locale) ? locale.trim() : "pt_BR");
                user.setTimezone(StringUtils.hasText(timezone) ? timezone.trim() : "America/Sao_Paulo");

                user.setSuspendedByAccount(false);
                user.setSuspendedByAdmin(false);

                Set<TenantPermission> basePermissions = new LinkedHashSet<>(TenantRolePermissions.permissionsFor(role));
                Set<TenantPermission> desiredPermissions = new LinkedHashSet<>();

                if (requestedPermissions != null && !requestedPermissions.isEmpty()) {
                    desiredPermissions.addAll(requestedPermissions);
                }

                desiredPermissions = PermissionScopeValidator.validateTenantPermissionsStrict(desiredPermissions);

                Set<TenantPermission> finalPermissions = new LinkedHashSet<>(basePermissions);
                finalPermissions.addAll(desiredPermissions);
                user.setPermissions(finalPermissions);

                TenantUser savedUser = tenantUserRepository.save(user);

                log.info(
                        "Usuário salvo com sucesso no tenant. accountId={}, tenantSchema={}, userId={}, email={}, role={}",
                        accountId,
                        normalizedTenantSchema,
                        savedUser.getId(),
                        savedUser.getEmail(),
                        savedUser.getRole()
                );

                savedEmail.set(savedUser.getEmail());
                savedUserId.set(savedUser.getId());

                recordAudit(
                        SecurityAuditActionType.USER_CREATED,
                        AuditOutcome.SUCCESS,
                        actor,
                        savedUser.getEmail(),
                        savedUser.getId(),
                        accountId,
                        normalizedTenantSchema,
                        m(
                                "scope", SCOPE,
                                "stage", "after_save",
                                "role", role.name(),
                                "finalPermissionsCount", sizeOrZero(savedUser.getPermissions())
                        )
                );

                recordAudit(
                        SecurityAuditActionType.PERMISSIONS_CHANGED,
                        AuditOutcome.SUCCESS,
                        actor,
                        savedUser.getEmail(),
                        savedUser.getId(),
                        accountId,
                        normalizedTenantSchema,
                        m(
                                "scope", SCOPE,
                                "reason", "create",
                                "baseCount", basePermissions.size(),
                                "requestedCount", requestedCount,
                                "finalCount", finalPermissions.size()
                        )
                );

                return savedUser;
            } catch (ApiException ex) {
                log.error(
                        "Falha de negócio ao criar usuário tenant. accountId={}, tenantSchema={}, email={}, status={}, error={}, msg={}",
                        accountId,
                        normalizedTenantSchema,
                        normEmail,
                        ex.getStatus(),
                        ex.getError(),
                        ex.getMessage()
                );

                recordAudit(
                        SecurityAuditActionType.USER_CREATED,
                        outcomeFrom(ex),
                        actor,
                        normEmail,
                        null,
                        accountId,
                        normalizedTenantSchema,
                        failureDetails(SCOPE, ex)
                );
                throw ex;
            } catch (Exception ex) {
                log.error(
                        "Falha inesperada ao criar usuário tenant. accountId={}, tenantSchema={}, email={}",
                        accountId,
                        normalizedTenantSchema,
                        normEmail,
                        ex
                );

                recordAudit(
                        SecurityAuditActionType.USER_CREATED,
                        AuditOutcome.FAILURE,
                        actor,
                        normEmail,
                        null,
                        accountId,
                        normalizedTenantSchema,
                        unexpectedFailureDetails(SCOPE, ex)
                );
                throw ex;
            }
        });

        final String finalEmail = savedEmail.get();
        final Long finalUserId = savedUserId.get();

        if (StringUtils.hasText(finalEmail) && accountId != null) {
            afterTransactionCompletion.runAfterCompletion(() -> {
                try {
                    loginIdentityService.ensureTenantIdentity(finalEmail, accountId);
                    log.info(
                            "ensureTenantIdentity executado após completion. accountId={}, userId={}, email={}",
                            accountId,
                            finalUserId,
                            finalEmail
                    );
                } catch (Exception e) {
                    log.error(
                            "Falha ao garantir identidade de login após completion (best-effort). accountId={}, userId={}, email={}",
                            accountId,
                            finalUserId,
                            finalEmail,
                            e
                    );
                }
            });
        }

        log.info(
                "Criação de usuário tenant finalizada com sucesso. accountId={}, tenantSchema={}, userId={}, email={}",
                accountId,
                normalizedTenantSchema,
                saved.getId(),
                saved.getEmail()
        );

        return saved;
    }

    /**
     * Valida parâmetros obrigatórios para criação de usuário.
     *
     * @param accountId id da conta
     * @param tenantSchema schema tenant
     * @param name nome
     * @param email email
     * @param rawPassword senha
     * @param role papel
     */
    private void validateCreateInputs(
            Long accountId,
            String tenantSchema,
            String name,
            String email,
            String rawPassword,
            TenantRole role
    ) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatorio", 400);
        }
        if (!StringUtils.hasText(tenantSchema)) {
            throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatorio", 400);
        }
        if (!StringUtils.hasText(name)) {
            throw new ApiException(ApiErrorCode.INVALID_NAME, "Nome é obrigatorio", 400);
        }
        if (!StringUtils.hasText(email)) {
            throw new ApiException(ApiErrorCode.INVALID_EMAIL, "Email é obrigatorio", 400);
        }
        if (!StringUtils.hasText(rawPassword)) {
            throw new ApiException(ApiErrorCode.INVALID_PASSWORD, "Senha é obrigatoria", 400);
        }
        if (role == null) {
            throw new ApiException(ApiErrorCode.INVALID_ROLE, "Role é obrigatoria", 400);
        }
    }

    public void setSuspendedByAdmin(Long accountId, String tenantSchema, Long userId, boolean suspended) {
        setSuspension(accountId, tenantSchema, userId, suspended, true);
    }

    public void setSuspendedByAccount(Long accountId, String tenantSchema, Long userId, boolean suspended) {
        setSuspension(accountId, tenantSchema, userId, suspended, false);
    }

    private void setSuspension(Long accountId, String tenantSchema, Long userId, boolean suspended, boolean byAdmin) {
        log.info("setSuspension INICIANDO | userId={} suspended={} byAdmin={}", userId, suspended, byAdmin);

        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatorio", 400);
        }
        if (!StringUtils.hasText(tenantSchema)) {
            throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatorio", 400);
        }
        if (userId == null) {
            throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatorio", 400);
        }

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
                requireWillStillHaveAtLeastOneActiveOwner(
                        accountId,
                        "Nao e permitido suspender o ultimo TENANT_OWNER ativo"
                );
            }

            SecurityAuditActionType action = suspended
                    ? SecurityAuditActionType.USER_SUSPENDED
                    : SecurityAuditActionType.USER_RESTORED;

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

                        if (updated == 0) {
                            throw new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuario não encontrado", 404);
                        }
                        return null;
                    }
            );

            return null;
        });

        if (!suspended && wasSuspended) {
            TenantUser userAfter = tenantSchemaUnitOfWork.readOnly(tenantSchema, () ->
                    tenantUserRepository.findIncludingDeletedByIdAndAccountId(userId, accountId).orElse(null)
            );

            if (userAfter != null && userAfter.isEnabledDomain()) {
                afterTransactionCompletion.runAfterCompletion(() -> {
                    try {
                        loginIdentityService.ensureTenantIdentity(userEmail, accountId);
                        log.info(
                                "ensureTenantIdentity executado após reativação. email={} accountId={} byAdmin={}",
                                userEmail,
                                accountId,
                                byAdmin
                        );
                    } catch (Exception e) {
                        log.error(
                                "Falha ao garantir identidade após reativação (best-effort). email={} accountId={}",
                                userEmail,
                                accountId,
                                e
                        );
                    }
                });
            }
        }
    }

    /**
     * Restaura usuário previamente deletado e reabilita identidade pública.
     *
     * @param userId id do usuário
     * @param accountId id da conta
     * @param tenantSchema schema tenant
     * @return usuário restaurado
     */
    public TenantUser restore(Long userId, Long accountId, String tenantSchema) {
        log.info("restore INICIANDO | userId={} accountId={}", userId, accountId);

        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatorio", 400);
        }
        if (!StringUtils.hasText(tenantSchema)) {
            throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatorio", 400);
        }
        if (userId == null) {
            throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatorio", 400);
        }

        AtomicReference<String> restoredEmail = new AtomicReference<>();

        TenantUser saved = tenantSchemaUnitOfWork.tx(tenantSchema, () -> {
            Actor actor = resolveActorOrNull(accountId, tenantSchema);

            TenantUser user = tenantUserRepository.findIncludingDeletedByIdAndAccountId(userId, accountId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuario não encontrado", 404));

            Map<String, Object> details = m("scope", SCOPE, "reason", "softRestore");

            TenantUser restoredUser = auditAttemptSuccessFail(
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

            restoredEmail.set(restoredUser.getEmail());
            return restoredUser;
        });

        if (StringUtils.hasText(restoredEmail.get()) && accountId != null) {
            afterTransactionCompletion.runAfterCompletion(() -> {
                try {
                    loginIdentityService.ensureTenantIdentity(restoredEmail.get(), accountId);
                    log.info(
                            "ensureTenantIdentity executado após restore. email={} accountId={}",
                            restoredEmail.get(),
                            accountId
                    );
                } catch (Exception e) {
                    log.error(
                            "Falha ao garantir identidade após restore (best-effort). email={} accountId={}",
                            restoredEmail.get(),
                            accountId,
                            e
                    );
                }
            });
        }

        log.info("restore FINALIZADO COM SUCESSO | userId={} accountId={}", userId, accountId);
        return saved;
    }

    public void softDelete(Long userId, Long accountId, String tenantSchema) {
        log.info("softDelete INICIANDO | userId={} accountId={}", userId, accountId);

        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatorio", 400);
        }
        if (!StringUtils.hasText(tenantSchema)) {
            throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatorio", 400);
        }
        if (userId == null) {
            throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatorio", 400);
        }

        AtomicReference<String> deletedEmail = new AtomicReference<>();

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
                requireWillStillHaveAtLeastOneActiveOwner(
                        accountId,
                        "Nao e permitido excluir o ultimo TENANT_OWNER ativo"
                );
            }

            deletedEmail.set(user.getEmail());

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

            return null;
        });

        if (StringUtils.hasText(deletedEmail.get()) && accountId != null) {
            afterTransactionCompletion.runAfterCompletion(() -> {
                try {
                    loginIdentityService.deleteTenantIdentity(deletedEmail.get(), accountId);
                    log.info(
                            "deleteTenantIdentity executado após softDelete. email={} accountId={}",
                            deletedEmail.get(),
                            accountId
                    );
                } catch (Exception e) {
                    log.error(
                            "Falha ao remover identidade após softDelete (best-effort). email={} accountId={}",
                            deletedEmail.get(),
                            accountId,
                            e
                    );
                }
            });
        }
    }

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

    /**
     * Persiste explicitamente um usuário no schema tenant informado.
     *
     * @param tenantSchema schema tenant
     * @param user entidade a persistir
     * @return entidade persistida
     */
    public TenantUser save(String tenantSchema, TenantUser user) {
        if (!StringUtils.hasText(tenantSchema)) {
            throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatorio", 400);
        }
        if (user == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "user é obrigatorio", 400);
        }

        log.info("Salvando usuário explicitamente. tenantSchema={}, userId={}, email={}", tenantSchema, user.getId(), user.getEmail());

        return tenantSchemaUnitOfWork.tx(tenantSchema.trim(), () -> tenantUserRepository.save(user));
    }

    public void transferTenantOwnerRole(Long accountId, String tenantSchema, Long fromUserId, Long toUserId) {
        // Implementacao existente
    }

    private void requireNotBuiltInForMutation(TenantUser user, String message) {
        if (user != null && user.getOrigin() == EntityOrigin.BUILT_IN) {
            throw new ApiException(ApiErrorCode.USER_BUILT_IN_IMMUTABLE, message);
        }
    }

    private boolean isActiveOwner(TenantUser user) {
        if (user == null) {
            return false;
        }
        if (user.isDeleted()) {
            return false;
        }
        if (user.isSuspendedByAccount()) {
            return false;
        }
        if (user.isSuspendedByAdmin()) {
            return false;
        }
        return user.getRole() != null && user.getRole().isTenantOwner();
    }

    private void requireWillStillHaveAtLeastOneActiveOwner(Long accountId, String message) {
        long owners = tenantUserRepository.countActiveOwnersByAccountId(accountId, TenantRole.TENANT_OWNER);
        if (owners <= 1) {
            throw new ApiException(ApiErrorCode.TENANT_OWNER_REQUIRED, message, 409);
        }
    }

    private static int sizeOrZero(Set<?> s) {
        return s == null ? 0 : s.size();
    }

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

            Object detailsToPersist = successDetails != null ? successDetails : attemptDetails;
            recordAudit(actionType, AuditOutcome.SUCCESS, actor, targetEmail, targetUserId, accountId, tenantSchema, detailsToPersist);

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
                log.warn(
                        "Falha ao gravar SecurityAudit (best-effort) | actionType={} outcome={} accountId={} tenantSchema={} msg={}",
                        actionType,
                        outcome,
                        accountId,
                        tenantSchema,
                        e.getMessage(),
                        e
                );
            }
        });
    }

    private String toJson(Object details) {
        if (details == null) {
            return null;
        }

        JsonNode node = jsonDetailsMapper.toJsonNode(details);
        if (node == null || node.isNull()) {
            return null;
        }

        return node.toString();
    }

    private Actor resolveActorOrNull(Long accountId, String tenantSchema) {
        try {
            Long actorUserId = securityUtils.getCurrentUserId();
            Long actorAccountId = securityUtils.getCurrentAccountId();

            if (actorUserId == null || actorAccountId == null) {
                return Actor.anonymous();
            }
            if (!actorAccountId.equals(accountId)) {
                return new Actor(actorUserId, null);
            }
            if (!StringUtils.hasText(tenantSchema)) {
                return new Actor(actorUserId, null);
            }

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
        if (ex == null) {
            return AuditOutcome.FAILURE;
        }
        int status = ex.getStatus();
        return (status == 401 || status == 403) ? AuditOutcome.DENIED : AuditOutcome.FAILURE;
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
        if (!StringUtils.hasText(msg)) {
            return null;
        }
        return msg.replace("\n", " ").replace("\r", " ").replace("\t", " ").trim();
    }

    private static Map<String, Object> m(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (kv == null) {
            return map;
        }
        for (int i = 0; i + 1 < kv.length; i += 2) {
            Object key = kv[i];
            Object value = kv[i + 1];
            if (key != null) {
                map.put(String.valueOf(key), value);
            }
        }
        return map;
    }

    private record Actor(Long userId, String email) {
        static Actor anonymous() {
            return new Actor(null, null);
        }
    }
}