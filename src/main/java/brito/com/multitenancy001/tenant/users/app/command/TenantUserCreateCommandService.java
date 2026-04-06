package brito.com.multitenancy001.tenant.users.app.command;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.infrastructure.tenant.TenantSchemaUnitOfWork;
import brito.com.multitenancy001.infrastructure.tx.AfterTransactionCompletion;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;
import brito.com.multitenancy001.shared.domain.common.EntityOrigin;
import brito.com.multitenancy001.shared.domain.service.LoginIdentityService;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.security.PermissionScopeValidator;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.shared.validation.ValidationPatterns;
import brito.com.multitenancy001.tenant.security.TenantPermission;
import brito.com.multitenancy001.tenant.security.TenantRole;
import brito.com.multitenancy001.tenant.security.TenantRolePermissions;
import brito.com.multitenancy001.tenant.subscription.app.TenantQuotaEnforcementService;
import brito.com.multitenancy001.tenant.subscription.app.TenantUsageSnapshotAfterCommitService;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import brito.com.multitenancy001.tenant.users.persistence.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Caso de uso de criação de usuários do tenant.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Validar payload e contexto mínimo.</li>
 *   <li>Executar enforcement de quota antes da escrita.</li>
 *   <li>Persistir o usuário dentro da transação tenant.</li>
 *   <li>Agendar side effects pós-transação de identidade e refresh de usage snapshot.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantUserCreateCommandService {

    private final TenantUserRepository tenantUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppClock appClock;
    private final TenantSchemaUnitOfWork tenantSchemaUnitOfWork;
    private final AfterTransactionCompletion afterTransactionCompletion;
    private final LoginIdentityService loginIdentityService;
    private final TenantQuotaEnforcementService tenantQuotaEnforcementService;
    private final TenantUserAuditService tenantUserAuditService;
    private final TenantUserActorResolver tenantUserActorResolver;
    private final TenantUsageSnapshotAfterCommitService tenantUsageSnapshotAfterCommitService;

    /**
     * Cria usuário tenant com pré-check de quota, persistência transacional
     * e side effects pós-transação.
     *
     * @param accountId id da conta
     * @param tenantSchema schema do tenant
     * @param name nome do usuário
     * @param email email do usuário
     * @param rawPassword senha em texto puro
     * @param role role tenant
     * @param phone telefone
     * @param avatarUrl avatar
     * @param locale locale
     * @param timezone timezone
     * @param requestedPermissions permissões explícitas
     * @param mustChangePassword flag de troca obrigatória
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

        final String normalizedTenantSchema = tenantSchema.trim();
        final String normalizedName = name.trim();
        final String normalizedEmail = EmailNormalizer.normalizeOrNull(email);

        if (!StringUtils.hasText(normalizedEmail) || !normalizedEmail.matches(ValidationPatterns.EMAIL_PATTERN)) {
            throw new ApiException(ApiErrorCode.INVALID_EMAIL, "Email invalido", 400);
        }

        if (!rawPassword.matches(ValidationPatterns.PASSWORD_PATTERN)) {
            throw new ApiException(ApiErrorCode.WEAK_PASSWORD, "Senha fraca", 400);
        }

        tenantQuotaEnforcementService.assertCanCreateUser(accountId, normalizedTenantSchema);

        AtomicReference<String> savedEmail = new AtomicReference<>();
        AtomicReference<Long> savedUserId = new AtomicReference<>();

        TenantUser saved = tenantSchemaUnitOfWork.tx(normalizedTenantSchema, () -> {
            final TenantUserAuditService.Actor actor =
                    tenantUserActorResolver.resolveActorOrNull(accountId, normalizedTenantSchema);
            final int requestedCount = requestedPermissions == null ? 0 : requestedPermissions.size();

            tenantUserAuditService.recordAudit(
                    SecurityAuditActionType.USER_CREATED,
                    AuditOutcome.ATTEMPT,
                    actor,
                    normalizedEmail,
                    null,
                    accountId,
                    normalizedTenantSchema,
                    tenantUserAuditService.m(
                            "scope", "TENANT",
                            "stage", "before_save",
                            "role", role.name(),
                            "requestedPermissionsCount", requestedCount
                    )
            );

            try {
                boolean exists = tenantUserRepository.existsByEmailAndAccountId(normalizedEmail, accountId);
                if (exists) {
                    throw new ApiException(ApiErrorCode.EMAIL_ALREADY_REGISTERED_IN_ACCOUNT);
                }

                TenantUser user = new TenantUser();
                user.setAccountId(accountId);
                user.rename(normalizedName);
                user.changeEmail(normalizedEmail);
                user.setPassword(passwordEncoder.encode(rawPassword));
                user.setRole(role);
                user.setOrigin(origin == null ? EntityOrigin.ADMIN : origin);
                user.setMustChangePassword(Boolean.TRUE.equals(mustChangePassword));

                Instant now = appClock.instant();
                user.setPasswordChangedAt(user.isMustChangePassword() ? null : now);

                user.setPhone(normalizeOptional(phone));
                user.setAvatarUrl(normalizeOptional(avatarUrl));
                user.setLocale(defaultIfBlank(locale, "pt_BR"));
                user.setTimezone(defaultIfBlank(timezone, "America/Sao_Paulo"));

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

                savedEmail.set(savedUser.getEmail());
                savedUserId.set(savedUser.getId());

                tenantUserAuditService.recordAudit(
                        SecurityAuditActionType.USER_CREATED,
                        AuditOutcome.SUCCESS,
                        actor,
                        savedUser.getEmail(),
                        savedUser.getId(),
                        accountId,
                        normalizedTenantSchema,
                        tenantUserAuditService.m(
                                "scope", "TENANT",
                                "stage", "after_save",
                                "role", role.name(),
                                "finalPermissionsCount", sizeOrZero(savedUser.getPermissions())
                        )
                );

                tenantUserAuditService.recordAudit(
                        SecurityAuditActionType.PERMISSIONS_CHANGED,
                        AuditOutcome.SUCCESS,
                        actor,
                        savedUser.getEmail(),
                        savedUser.getId(),
                        accountId,
                        normalizedTenantSchema,
                        tenantUserAuditService.m(
                                "scope", "TENANT",
                                "reason", "create",
                                "baseCount", basePermissions.size(),
                                "requestedCount", requestedCount,
                                "finalCount", finalPermissions.size()
                        )
                );

                return savedUser;
            } catch (ApiException ex) {
                tenantUserAuditService.recordAudit(
                        SecurityAuditActionType.USER_CREATED,
                        outcomeFrom(ex),
                        actor,
                        normalizedEmail,
                        null,
                        accountId,
                        normalizedTenantSchema,
                        tenantUserAuditService.m(
                                "scope", "TENANT",
                                "error", ex.getError(),
                                "status", ex.getStatus(),
                                "message", safeMessage(ex.getMessage())
                        )
                );
                throw ex;
            } catch (Exception ex) {
                tenantUserAuditService.recordAudit(
                        SecurityAuditActionType.USER_CREATED,
                        AuditOutcome.FAILURE,
                        actor,
                        normalizedEmail,
                        null,
                        accountId,
                        normalizedTenantSchema,
                        tenantUserAuditService.m(
                                "scope", "TENANT",
                                "unexpected", ex.getClass().getSimpleName(),
                                "message", safeMessage(ex.getMessage())
                        )
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

        tenantUsageSnapshotAfterCommitService.scheduleRefreshAfterCommit(
                accountId,
                normalizedTenantSchema
        );

        log.info(
                "Criação de usuário tenant finalizada com sucesso. accountId={}, tenantSchema={}, userId={}, email={}",
                accountId,
                normalizedTenantSchema,
                saved.getId(),
                saved.getEmail()
        );

        return saved;
    }

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
        if (!StringUtils.hasText(name) || !StringUtils.hasText(name.trim())) {
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

    private String normalizeOptional(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String defaultIfBlank(String value, String defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? defaultValue : normalized;
    }

    private static int sizeOrZero(Set<?> s) {
        return s == null ? 0 : s.size();
    }

    private AuditOutcome outcomeFrom(ApiException ex) {
        if (ex == null) {
            return AuditOutcome.FAILURE;
        }
        int status = ex.getStatus();
        return (status == 401 || status == 403) ? AuditOutcome.DENIED : AuditOutcome.FAILURE;
    }

    private String safeMessage(String msg) {
        if (!StringUtils.hasText(msg)) {
            return null;
        }
        return msg.replace("\n", " ").replace("\r", " ").replace("\t", " ").trim();
    }
}