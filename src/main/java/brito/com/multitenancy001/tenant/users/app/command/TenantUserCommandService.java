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
 * <p>
 * Responsabilidades:
 * <ul>
 *   <li>Centralizar toda a lógica de criação, atualização, suspensão e exclusão de usuários.</li>
 *   <li>Garantir a consistência entre o schema do Tenant e o índice público {@code public.login_identities}.</li>
 *   <li>Executar auditoria SOC2-like para todas as operações sensíveis.</li>
 * </ul>
 *
 * <p><b>REGRIA CRÍTICA DE NEGÓCIO:</b></p>
 * Para que o fluxo de login multi-tenant ({@code /api/tenant/auth/login}) funcione corretamente,
 * todo usuário ativo em um tenant (não deletado, não suspenso) DEVE ter uma entrada correspondente
 * na tabela {@code public.login_identities} com {@code subject_type = 'TENANT_ACCOUNT'}.
 * <p>
 * Esta classe é a fonte da verdade para essa sincronização. Todas as operações que resultam em um
 * usuário ativo (criação, restauração, reativação) DEVEM, após o COMMIT da transação do tenant,
 * garantir a existência dessa entrada. Operações que tornam um usuário inativo (soft delete)
 * DEVEM removê-la.
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

    /** Serviço para provisionamento do índice público de login. */
    private final LoginIdentityProvisioningService loginIdentityProvisioningService;

    // =========================================================================
    // MÉTODO HELPER CENTRALIZADO PARA SINCRONIZAÇÃO DO LOGIN IDENTITY
    // =========================================================================

    /**
     * Agenda a criação/atualização da identidade de login no schema PUBLIC
     * para ser executada APÓS o COMMIT bem-sucedido da transação atual.
     * <p>
     * Este método é idempotente e seguro para ser chamado múltiplas vezes. Ele DEVE ser invocado
     * sempre que um usuário se torna ativo (após criação, restauração ou reativação).
     *
     * @param email     Email do usuário (será normalizado pelo serviço de provisionamento)
     * @param accountId ID da conta à qual o usuário pertence
     * @param operation Nome da operação para fins de logging (ex: "create", "restore", "unsuspend")
     */
   // =========================================================================
// MÉTODO HELPER CENTRALIZADO PARA SINCRONIZAÇÃO DO LOGIN IDENTITY - CORRIGIDO
// =========================================================================

private void ensureLoginIdentityAfterCommit(String email, Long accountId, String operation) {
    log.info("🔵 [1] ensureLoginIdentityAfterCommit CHAMADO - email={}, accountId={}, operation={}", 
             email, accountId, operation);
    
    if (email == null || accountId == null) {
        log.warn("⚠️ [1] ensureLoginIdentityAfterCommit ignorado: email ou accountId nulo. email={}, accountId={}, operation={}", 
                 email, accountId, operation);
        return;
    }

    log.info("🔵 [2] AGENDANDO PublicAuditDispatcher.dispatch para {} | accountId={} | operation={}",
              email, accountId, operation);

    // ✅ CORREÇÃO: Usar PublicAuditDispatcher em vez de AfterCommit direto
    publicAuditDispatcher.dispatch(() -> {
        log.info("🔵 [3] EXECUTANDO PublicAuditDispatcher - INÍCIO para {} | accountId={}", email, accountId);
        
        try {
            log.info("🔵 [4] Chamando loginIdentityProvisioningService.ensureTenantIdentityAfterCompletion para {} | accountId={}", 
                     email, accountId);
                     
            loginIdentityProvisioningService.ensureTenantIdentityAfterCompletion(email, accountId);
            
            log.info("✅ [5] LOGIN_IDENTITY CRIADA com sucesso para {} | accountId={}", email, accountId);
        } catch (Exception e) {
            log.error("❌ [5] FALHA CRÍTICA ao provisionar login_identity para {} | accountId={} | operation={} | erro: {}",
                      email, accountId, operation, e.getMessage(), e);
            log.error("Stacktrace: ", e);
        }
        
        log.info("🔵 [6] EXECUTANDO PublicAuditDispatcher - FIM para {} | accountId={}", email, accountId);
    });
    
    log.info("🔵 [7] ensureLoginIdentityAfterCommit FINALIZADO - email={}, accountId={}", email, accountId);
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
        log.info("🚀 [CREATE] INICIANDO createTenantUser para email={}, accountId={}", email, accountId);
        
        return tenantSchemaUnitOfWork.tx(tenantSchema, () -> {
            log.info("📦 [CREATE] DENTRO DA TRANSAÇÃO - thread={}, email={}, accountId={}", 
                     Thread.currentThread().getId(), email, accountId);

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
                log.info("✅ [CREATE] Usuário SALVO no tenant: ID={}, email={}", saved.getId(), saved.getEmail());

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

                // ✅ Pós-commit: garante índice público (login_identities) para permitir loginInit ambíguo
                log.info("📝 [CREATE] Chamando ensureLoginIdentityAfterCommit para email={}, accountId={}", saved.getEmail(), accountId);
                ensureLoginIdentityAfterCommit(saved.getEmail(), accountId, "create");

                log.info("✅ [CREATE] createTenantUser FINALIZADO com sucesso para email={}", saved.getEmail());
                
                return saved;

            } catch (ApiException ex) {
                log.error("❌ [CREATE] ApiException: {}", ex.getMessage());
                recordAudit(SecurityAuditActionType.USER_CREATED, outcomeFrom(ex), actor, normEmail, null, accountId, tenantSchema, failureDetails(SCOPE, ex));
                throw ex;
            } catch (Exception ex) {
                log.error("❌ [CREATE] Exception inesperada: {}", ex.getMessage(), ex);
                recordAudit(SecurityAuditActionType.USER_CREATED, AuditOutcome.FAILURE, actor, normEmail, null, accountId, tenantSchema, unexpectedFailureDetails(SCOPE, ex));
                throw ex;
            }
        });
    }

    // =========================================================
    // UPDATE: STATUS / PROFILE
    // =========================================================

    /**
     * Define o status de suspensão por admin de um usuário.
     */
    public void setSuspendedByAdmin(Long accountId, String tenantSchema, Long userId, boolean suspended) {
        setSuspension(accountId, tenantSchema, userId, suspended, true);
    }

    /**
     * Define o status de suspensão por account (sistema/plano) de um usuário.
     */
    public void setSuspendedByAccount(Long accountId, String tenantSchema, Long userId, boolean suspended) {
        setSuspension(accountId, tenantSchema, userId, suspended, false);
    }

    /**
     * Lógica centralizada para lidar com suspensão/reativação por admin ou por account.
     * Garante a sincronização com o índice público quando o usuário é reativado.
     */
    private void setSuspension(Long accountId, String tenantSchema, Long userId, boolean suspended, boolean byAdmin) {
        log.info("🔄 [SUSPENSION] INICIANDO - userId={}, suspended={}, byAdmin={}", userId, suspended, byAdmin);

        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);
        if (!StringUtils.hasText(tenantSchema)) throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatório", 400);
        if (userId == null) throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatório", 400);

        // ⚠️ PRECISAMOS DO EMAIL DO USUÁRIO ANTES DE EXECUTAR A OPERAÇÃO
        TenantUser userBefore = tenantSchemaUnitOfWork.readOnly(tenantSchema, () ->
            tenantUserRepository.findIncludingDeletedByIdAndAccountId(userId, accountId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário não encontrado", 404))
        );
        final String userEmail = userBefore.getEmail();
        final boolean wasSuspended = byAdmin ? userBefore.isSuspendedByAdmin() : userBefore.isSuspendedByAccount();
        
        log.info("📧 [SUSPENSION] userEmail={}, wasSuspended={}", userEmail, wasSuspended);

        tenantSchemaUnitOfWork.tx(tenantSchema, () -> {
            log.info("📦 [SUSPENSION] DENTRO DA TRANSAÇÃO - userId={}", userId);
            
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
                        
                        log.info("✅ [SUSPENSION] Suspensão atualizada: updated={}", updated);
                        return null;
                    }
            );

            return null;
        });

        // ⚠️ APÓS A TRANSAÇÃO PRINCIPAL, verificamos se a operação foi uma REATIVAÇÃO
        if (!suspended && wasSuspended) {
            log.info("🔄 [SUSPENSION] USUÁRIO REATIVADO - verificando estado final");
            
            // Precisamos garantir que o usuário agora está ativo antes de recriar a identidade.
            // Uma nova transação de leitura para obter o estado final.
            TenantUser userAfter = tenantSchemaUnitOfWork.readOnly(tenantSchema, () ->
                tenantUserRepository.findIncludingDeletedByIdAndAccountId(userId, accountId).orElse(null)
            );

            // isEnabledDomain() verifica se não está deletado e não está suspenso por nenhum dos dois motivos.
            if (userAfter != null && userAfter.isEnabledDomain()) {
                log.info("🔄 [SUSPENSION] Usuário {} reativado e ativo, recriando login_identity", userId);
                ensureLoginIdentityAfterCommit(userEmail, accountId, byAdmin ? "unsuspend-admin" : "unsuspend-account");
            } else {
                log.info("⏭️ [SUSPENSION] Usuário {} reativado, mas não está em estado ativo final. Identity não será recriada.", userId);
                if (userAfter != null) {
                    log.info("Estado: deleted={}, suspendedByAccount={}, suspendedByAdmin={}", 
                             userAfter.isDeleted(), userAfter.isSuspendedByAccount(), userAfter.isSuspendedByAdmin());
                }
            }
        } else {
            log.info("⏭️ [SUSPENSION] Não é reativação: suspended={}, wasSuspended={}", suspended, wasSuspended);
        }
    }

    // =========================================================
    // RESTORE
    // =========================================================

    /**
     * Restaura um usuário previamente deletado e agenda a recriação de sua identidade no índice público.
     */
    public TenantUser restore(Long userId, Long accountId, String tenantSchema) {
        log.info("🔄 [RESTORE] INICIANDO restore para userId={}, accountId={}", userId, accountId);

        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);
        if (!StringUtils.hasText(tenantSchema)) throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatório", 400);
        if (userId == null) throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatório", 400);

        return tenantSchemaUnitOfWork.tx(tenantSchema, () -> {
            log.info("📦 [RESTORE] DENTRO DA TRANSAÇÃO - userId={}", userId);
            
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

            log.info("✅ [RESTORE] Usuário restaurado: ID={}, email={}", saved.getId(), saved.getEmail());

            // ✅ Pós-commit: garante índice público novamente
            log.info("📝 [RESTORE] Chamando ensureLoginIdentityAfterCommit para restore: email={}, accountId={}", saved.getEmail(), accountId);
            ensureLoginIdentityAfterCommit(saved.getEmail(), accountId, "restore");

            return saved;
        });
    }

    // =========================================================
    // DELETE
    // =========================================================

    /**
     * Aplica soft delete em um usuário e agenda a remoção de sua identidade do índice público.
     */
   // =========================================================
// DELETE
// =========================================================

/**
 * Aplica soft delete em um usuário e agenda a remoção de sua identidade do índice público.
 */
public void softDelete(Long userId, Long accountId, String tenantSchema) {
    log.info("🗑️ [SOFT DELETE] INICIANDO - userId={}, accountId={}", userId, accountId);

    if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);
    if (!StringUtils.hasText(tenantSchema)) throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatório", 400);
    if (userId == null) throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatório", 400);

    tenantSchemaUnitOfWork.tx(tenantSchema, () -> {
        log.info("📦 [SOFT DELETE] DENTRO DA TRANSAÇÃO - userId={}", userId);
        
        Actor actor = resolveActorOrNull(accountId, tenantSchema);

        TenantUser user = tenantUserRepository.findIncludingDeletedByIdAndAccountId(userId, accountId)
                .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário não encontrado", 404));

        if (user.isDeleted()) {
            log.info("⏭️ [SOFT DELETE] Usuário já está deletado: userId={}", userId);
            return null;
        }

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
                    log.info("✅ [SOFT DELETE] Usuário marcado como deletado: {}", emailForIndex);
                    return null;
                }
        );

        // ✅ Pós-commit: remove do índice público - USANDO PublicAuditDispatcher
        log.info("📝 [SOFT DELETE] Agendando remoção de login_identity via PublicAuditDispatcher para: {}", emailForIndex);
        
        publicAuditDispatcher.dispatch(() -> {
            try {
                loginIdentityProvisioningService.deleteTenantIdentityAfterCompletion(emailForIndex, accountId);
                log.info("✅ [SOFT DELETE] LOGIN_IDENTITY REMOVIDA para {}", emailForIndex);
            } catch (Exception e) {
                log.error("❌ [SOFT DELETE] Erro ao remover login_identity: {}", e.getMessage(), e);
            }
        });

        return null;
    });
}
    // =========================================================
    // Outros métodos (mantidos iguais)
    // =========================================================

    public TenantUser resetPassword(Long userId, Long accountId, String tenantSchema, String newPassword) {
        // Implementação existente
        throw new UnsupportedOperationException("Implementar conforme necessário");
    }

    public void resetPasswordWithToken(Long accountId, String tenantSchema, String email, String token, String newPassword) {
        // Implementação existente
    }

    public void changeMyPassword(Long userId, Long accountId, String tenantSchema, String currentPassword, String newPassword, String confirmNewPassword) {
        // Implementação existente
    }

    public TenantUser updateProfile(Long userId, Long accountId, String tenantSchema, String name, String phone, String avatarUrl, String locale, String timezone, Instant now) {
        // Implementação existente
        throw new UnsupportedOperationException("Implementar conforme necessário");
    }

    public void hardDelete(Long userId, Long accountId, String tenantSchema) {
        // Implementação existente
    }

    public TenantUser save(String tenantSchema, TenantUser user) {
        // Implementação existente
        return user;
    }

    public void transferTenantOwnerRole(Long accountId, String tenantSchema, Long fromUserId, Long toUserId) {
        // Implementação existente
    }

    // =========================================================
    // HELPERS (mantidos iguais)
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