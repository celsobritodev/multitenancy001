package brito.com.multitenancy001.controlplane.users.app;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.controlplane.security.ControlPlaneRole;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneChangeMyPasswordRequest;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneMeResponse;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserCreateRequest;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserDetailsResponse;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserPasswordResetRequest;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserPermissionsUpdateRequest;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserSuspendRequest;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserUpdateRequest;
import brito.com.multitenancy001.controlplane.users.domain.ControlPlaneBuiltInUsers;
import brito.com.multitenancy001.controlplane.users.domain.ControlPlaneUser;
import brito.com.multitenancy001.controlplane.users.persistence.ControlPlaneUserRepository;
import brito.com.multitenancy001.integration.security.ControlPlaneRequestIdentityService;
import brito.com.multitenancy001.infrastructure.publicschema.audit.SecurityAuditService;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;
import brito.com.multitenancy001.shared.domain.common.EntityOrigin;
import brito.com.multitenancy001.shared.domain.service.LoginIdentityService; // NOVA INTERFACE
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.json.JsonDetailsMapper;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.security.SystemRoleName;
import brito.com.multitenancy001.shared.time.AppClock;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Use cases de usuários do Control Plane.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ControlPlaneUserService {

    private static final String BUILTIN_IMMUTABLE_MESSAGE =
            "Usuário BUILT_IN é protegido: não pode ser alterado/deletado/restaurado/suspenso; apenas senha pode ser trocada.";

    private static final String MSG_CP_ACCOUNT_INVALID =
            "Configuração inválida: conta do Control Plane ausente ou duplicada.";

    private static final String SCOPE = "CONTROL_PLANE";

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final AccountRepository accountRepository;
    private final ControlPlaneUserRepository controlPlaneUserRepository;
    private final ControlPlaneRequestIdentityService requestIdentity;
    private final ControlPlaneUserExplicitPermissionsService explicitPermissionsService;
    private final PasswordEncoder passwordEncoder;
    private final AppClock appClock;

    // ✅ NOVO: usando a interface, não a implementação concreta
    private final LoginIdentityService loginIdentityService;

    private final SecurityAuditService securityAuditService;
    private final JsonDetailsMapper jsonDetailsMapper;

    // =========================================================
    // LOGIN_IDENTITY SYNC (PUBLIC) - HELPERS
    // =========================================================

    private void ensureControlPlaneIdentityNow(String email, Long userId, String operation) {
        log.info("ensureControlPlaneIdentityNow INICIANDO | email={} userId={} op={}", email, userId, operation);
        try {
            loginIdentityService.ensureControlPlaneIdentity(email, userId);
            log.info("✅ ensureControlPlaneIdentityNow CONCLUÍDO | email={} userId={}", email, userId);
        } catch (Exception e) {
            log.error("❌ ensureControlPlaneIdentityNow FALHOU | email={} userId={} | erro: {}", 
                    email, userId, e.getMessage(), e);
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, 
                "Falha ao garantir identidade de login para usuário do Control Plane", 500);
        }
    }

    private void moveControlPlaneIdentityNow(Long userId, String newEmail, String operation) {
        log.info("moveControlPlaneIdentityNow INICIANDO | userId={} newEmail={} op={}", userId, newEmail, operation);
        try {
            loginIdentityService.moveControlPlaneIdentity(userId, newEmail);
            log.info("✅ moveControlPlaneIdentityNow CONCLUÍDO | userId={} newEmail={}", userId, newEmail);
        } catch (Exception e) {
            log.error("❌ moveControlPlaneIdentityNow FALHOU | userId={} newEmail={} | erro: {}", 
                    userId, newEmail, e.getMessage(), e);
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, 
                "Falha ao mover identidade de login", 500);
        }
    }

    private void deleteControlPlaneIdentityNow(Long userId, String operation) {
        log.info("deleteControlPlaneIdentityNow INICIANDO | userId={} op={}", userId, operation);
        try {
            loginIdentityService.deleteControlPlaneIdentityByUserId(userId);
            log.info("✅ deleteControlPlaneIdentityNow CONCLUÍDO | userId={}", userId);
        } catch (Exception e) {
            log.error("❌ deleteControlPlaneIdentityNow FALHOU (best-effort) | userId={} | erro: {}", 
                    userId, e.getMessage(), e);
            // Não relança - operação best-effort
        }
    }

    // =========================================================
    // ADMIN ENDPOINTS
    // =========================================================

    public ControlPlaneUserDetailsResponse createControlPlaneUser(ControlPlaneUserCreateRequest request) {
        log.info("🚀 createControlPlaneUser INICIANDO | email={}", request != null ? request.email() : null);

        return publicSchemaUnitOfWork.tx(() -> {
            Actor actor = resolveActorOrNull();

            if (request == null) throw new ApiException(ApiErrorCode.INVALID_REQUEST, "request é obrigatório", 400);
            if (request.role() == null) throw new ApiException(ApiErrorCode.ROLE_REQUIRED, "role é obrigatório", 400);
            if (request.password() == null || request.password().isBlank()) {
                throw new ApiException(ApiErrorCode.INVALID_PASSWORD, "senha é obrigatória", 400);
            }

            Account cp = getControlPlaneAccount();

            String email = normalizeEmailOrThrow(request.email());
            if (ControlPlaneBuiltInUsers.isReservedEmail(email)) {
                throw new ApiException(ApiErrorCode.EMAIL_RESERVED, "Este email é reservado do sistema (BUILT_IN)", 409);
            }

            AuditTarget target = new AuditTarget(email, null);

            Map<String, Object> attempt = m(
                    "scope", SCOPE,
                    "stage", "before_save",
                    "role", request.role() == null ? null : request.role().name(),
                    "permissionsCount", request.permissions() == null ? 0 : request.permissions().size()
            );

            Supplier<Object> successSupplier = () -> m(
                    "scope", SCOPE,
                    "stage", "after_save",
                    "role", request.role() == null ? null : request.role().name(),
                    "permissionsCount", request.permissions() == null ? 0 : request.permissions().size()
            );

            return auditAttemptSuccessFail(
                    SecurityAuditActionType.USER_CREATED,
                    actor,
                    target,
                    cp.getId(),
                    null,
                    attempt,
                    successSupplier,
                    () -> {
                        boolean emailExists = controlPlaneUserRepository
                                .findByEmailAndAccount_IdAndDeletedFalse(email, cp.getId())
                                .isPresent();
                        if (emailExists) {
                            throw new ApiException(ApiErrorCode.EMAIL_ALREADY_IN_USE, "Já existe um usuário ativo com este email", 409);
                        }

                        String name = normalizeNameOrThrow(request.name());
                        ControlPlaneRole role = request.role();
                        String hash = passwordEncoder.encode(request.password());

                        ControlPlaneUser user = ControlPlaneUser.builder()
                                .account(cp)
                                .origin(EntityOrigin.ADMIN)
                                .name(name)
                                .email(email)
                                .role(role)
                                .build();

                        user.setTemporaryPasswordHash(hash);

                        ControlPlaneUser saved = controlPlaneUserRepository.save(user);

                        if (request.permissions() != null && !request.permissions().isEmpty()) {
                            explicitPermissionsService.setExplicitPermissionsFromCodes(saved.getId(), request.permissions());

                            recordAudit(
                                    SecurityAuditActionType.PERMISSIONS_CHANGED,
                                    AuditOutcome.SUCCESS,
                                    actor,
                                    saved.getEmail(),
                                    saved.getId(),
                                    cp.getId(),
                                    null,
                                    m(
                                            "scope", SCOPE,
                                            "reason", "create",
                                            "permissionsCount", request.permissions().size(),
                                            "permissions", request.permissions()
                                    )
                            );
                        }

                        // ✅ Garante identidade de forma SÍNCRONA
                        ensureControlPlaneIdentityNow(saved.getEmail(), saved.getId(), "create");

                        log.info("✅ createControlPlaneUser CONCLUÍDO | id={} email={}", saved.getId(), saved.getEmail());
                        return getControlPlaneUser(saved.getId());
                    }
            );
        });
    }

    public ControlPlaneUserDetailsResponse updateControlPlaneUser(Long userId, ControlPlaneUserUpdateRequest request) {
        log.info("updateControlPlaneUser INICIANDO | userId={}", userId);

        return publicSchemaUnitOfWork.tx(() -> {
            Actor actor = resolveActorOrNull();

            if (userId == null) throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatório", 400);
            if (request == null) throw new ApiException(ApiErrorCode.INVALID_REQUEST, "request é obrigatório", 400);

            Account cp = getControlPlaneAccount();

            ControlPlaneUser user = controlPlaneUserRepository
                    .findNotDeletedByIdAndAccountId(userId, cp.getId())
                    .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário não encontrado", 404));

            if (user.isBuiltInUser()) {
                throw new ApiException(ApiErrorCode.USER_BUILT_IN_IMMUTABLE, BUILTIN_IMMUTABLE_MESSAGE, 409);
            }

            String beforeName = user.getName();
            String beforeEmail = user.getEmail();
            ControlPlaneRole beforeRole = user.getRole();

            AuditTarget target = new AuditTarget(user.getEmail(), user.getId());

            Map<String, Object> attempt = m(
                    "scope", SCOPE,
                    "reason", "update",
                    "hasName", request.name() != null,
                    "hasEmail", request.email() != null,
                    "hasRole", request.role() != null,
                    "hasPermissions", request.permissions() != null
            );

            Map<String, Object> success = new LinkedHashMap<>();
            success.put("scope", SCOPE);
            success.put("reason", "update");

            return auditAttemptSuccessFail(
                    SecurityAuditActionType.USER_UPDATED,
                    actor,
                    target,
                    cp.getId(),
                    null,
                    attempt,
                    () -> success,
                    () -> {
                        boolean roleChanged = false;
                        boolean permissionsChanged = request.permissions() != null;

                        Map<String, Object> changes = new LinkedHashMap<>();

                        // NAME
                        if (request.name() != null) {
                            String newName = normalizeNameOrThrow(request.name());
                            String currentName = user.getName();

                            if (currentName == null || !currentName.equals(newName)) {
                                user.rename(newName);
                                changes.put("nameBefore", beforeName);
                                changes.put("nameAfter", newName);
                            }
                        }

                        // EMAIL
                        if (request.email() != null) {
                            String newEmail = normalizeEmailOrThrow(request.email());

                            if (ControlPlaneBuiltInUsers.isReservedEmail(newEmail)) {
                                throw new ApiException(ApiErrorCode.EMAIL_RESERVED, "Este email é reservado do sistema (BUILT_IN)", 409);
                            }

                            String currentEmail = EmailNormalizer.normalizeOrNull(user.getEmail());
                            if (currentEmail == null || !currentEmail.equals(newEmail)) {

                                boolean emailExists = controlPlaneUserRepository
                                        .findByEmailAndAccount_IdAndDeletedFalse(newEmail, cp.getId())
                                        .filter(u -> !u.getId().equals(user.getId()))
                                        .isPresent();

                                if (emailExists) {
                                    throw new ApiException(ApiErrorCode.EMAIL_ALREADY_IN_USE, "Já existe um usuário ativo com este email", 409);
                                }

                                user.changeEmail(newEmail);
                                
                                // ✅ Move identidade de forma SÍNCRONA
                                moveControlPlaneIdentityNow(user.getId(), newEmail, "update");

                                changes.put("emailBefore", beforeEmail);
                                changes.put("emailAfter", newEmail);
                            }
                        }

                        // ROLE
                        if (request.role() != null) {
                            if (beforeRole == null || !beforeRole.equals(request.role())) {
                                user.changeRole(request.role());
                                roleChanged = true;

                                changes.put("roleBefore", beforeRole == null ? null : beforeRole.name());
                                changes.put("roleAfter", user.getRole() == null ? null : user.getRole().name());
                            }
                        }

                        controlPlaneUserRepository.save(user);

                        // PERMISSIONS (evento separado)
                        if (permissionsChanged) {
                            explicitPermissionsService.setExplicitPermissionsFromCodes(userId, request.permissions());

                            recordAudit(
                                    SecurityAuditActionType.PERMISSIONS_CHANGED,
                                    AuditOutcome.SUCCESS,
                                    actor,
                                    user.getEmail(),
                                    user.getId(),
                                    cp.getId(),
                                    null,
                                    m(
                                            "scope", SCOPE,
                                            "reason", "update",
                                            "permissionsCount", request.permissions() == null ? 0 : request.permissions().size(),
                                            "permissions", request.permissions()
                                    )
                            );

                            changes.put("permissionsChanged", true);
                            changes.put("permissionsCount", request.permissions() == null ? 0 : request.permissions().size());
                        }

                        // ROLE_CHANGED (evento separado)
                        if (roleChanged) {
                            recordAudit(
                                    SecurityAuditActionType.ROLE_CHANGED,
                                    AuditOutcome.SUCCESS,
                                    actor,
                                    user.getEmail(),
                                    user.getId(),
                                    cp.getId(),
                                    null,
                                    m(
                                            "scope", SCOPE,
                                            "from", beforeRole == null ? null : beforeRole.name(),
                                            "to", user.getRole() == null ? null : user.getRole().name()
                                    )
                            );
                        }

                        success.put("changed", !changes.isEmpty());
                        success.put("changes", changes);

                        log.info("✅ updateControlPlaneUser CONCLUÍDO | userId={}", userId);
                        return getControlPlaneUser(userId);
                    }
            );
        });
    }

    public ControlPlaneUserDetailsResponse restoreControlPlaneUser(Long userId) {
        log.info("restoreControlPlaneUser INICIANDO | userId={}", userId);

        return publicSchemaUnitOfWork.tx(() -> {
            Actor actor = resolveActorOrNull();

            if (userId == null) throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatório", 400);

            Account cp = getControlPlaneAccount();

            ControlPlaneUser user = controlPlaneUserRepository.findById(userId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário não encontrado", 404));

            if (user.getAccount() == null || user.getAccount().getId() == null || !user.getAccount().getId().equals(cp.getId())) {
                throw new ApiException(ApiErrorCode.USER_OUT_OF_SCOPE, "Usuário não pertence ao Control Plane", 403);
            }

            if (user.isBuiltInUser()) {
                throw new ApiException(ApiErrorCode.USER_BUILT_IN_IMMUTABLE, BUILTIN_IMMUTABLE_MESSAGE, 409);
            }

            AuditTarget target = new AuditTarget(user.getEmail(), user.getId());

            Map<String, Object> attempt = m(
                    "scope", SCOPE,
                    "reason", "soft_restore"
            );

            ControlPlaneUserDetailsResponse result = auditAttemptSuccessFail(
                    SecurityAuditActionType.USER_SOFT_RESTORED,
                    actor,
                    target,
                    cp.getId(),
                    null,
                    attempt,
                    () -> attempt,
                    () -> {
                        user.restore();
                        ControlPlaneUser saved = controlPlaneUserRepository.save(user);

                        // ✅ Garante identidade de forma SÍNCRONA
                        ensureControlPlaneIdentityNow(saved.getEmail(), saved.getId(), "restore");

                        return mapToResponse(saved);
                    }
            );

            log.info("✅ restoreControlPlaneUser CONCLUÍDO | userId={}", userId);
            return result;
        });
    }

    // =========================================================
    // Outros métodos (mantidos como estavam, apenas com logs)
    // =========================================================

    public List<ControlPlaneUserDetailsResponse> listControlPlaneUsers() {
        log.debug("listControlPlaneUsers chamado");
        return publicSchemaUnitOfWork.readOnly(() -> {
            Account cp = getControlPlaneAccount();
            return controlPlaneUserRepository.findNotDeletedByAccountId(cp.getId()).stream()
                    .map(this::mapToResponse)
                    .toList();
        });
    }

    public ControlPlaneUserDetailsResponse getControlPlaneUser(Long userId) {
        log.debug("getControlPlaneUser chamado | userId={}", userId);
        return publicSchemaUnitOfWork.readOnly(() -> {
            if (userId == null) throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatório", 400);

            Account cp = getControlPlaneAccount();

            ControlPlaneUser user = controlPlaneUserRepository.findById(userId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário não encontrado", 404));

            if (user.getAccount() == null || user.getAccount().getId() == null || !user.getAccount().getId().equals(cp.getId())) {
                throw new ApiException(ApiErrorCode.USER_OUT_OF_SCOPE, "Usuário não pertence ao Control Plane", 403);
            }

            return mapToResponse(user);
        });
    }

    public ControlPlaneUserDetailsResponse updateControlPlaneUserPermissions(Long userId, ControlPlaneUserPermissionsUpdateRequest request) {
        log.info("updateControlPlaneUserPermissions INICIANDO | userId={}", userId);
        
        return publicSchemaUnitOfWork.tx(() -> {
            Actor actor = resolveActorOrNull();

            if (userId == null) throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatório", 400);
            if (request == null) throw new ApiException(ApiErrorCode.INVALID_REQUEST, "request é obrigatório", 400);

            Account cp = getControlPlaneAccount();

            ControlPlaneUser targetUser = controlPlaneUserRepository.findById(userId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário não encontrado", 404));

            if (targetUser.getAccount() == null || targetUser.getAccount().getId() == null || !targetUser.getAccount().getId().equals(cp.getId())) {
                throw new ApiException(ApiErrorCode.USER_OUT_OF_SCOPE, "Usuário não pertence ao Control Plane", 403);
            }

            if (targetUser.isBuiltInUser()) {
                throw new ApiException(ApiErrorCode.USER_BUILT_IN_IMMUTABLE, BUILTIN_IMMUTABLE_MESSAGE, 409);
            }

            int permCount = (request.permissions() == null) ? 0 : request.permissions().size();

            AuditTarget target = new AuditTarget(targetUser.getEmail(), targetUser.getId());

            Map<String, Object> attempt = m(
                    "scope", SCOPE,
                    "reason", "permissions_endpoint",
                    "permissionsCount", permCount
            );

            ControlPlaneUserDetailsResponse result = auditAttemptSuccessFail(
                    SecurityAuditActionType.PERMISSIONS_CHANGED,
                    actor,
                    target,
                    cp.getId(),
                    null,
                    attempt,
                    null,
                    () -> {
                        explicitPermissionsService.setExplicitPermissionsFromCodes(userId, request.permissions());
                        return getControlPlaneUser(userId);
                    }
            );

            log.info("✅ updateControlPlaneUserPermissions CONCLUÍDO | userId={}", userId);
            return result;
        });
    }

    public void resetControlPlaneUserPassword(Long userId, ControlPlaneUserPasswordResetRequest request) {
        log.info("resetControlPlaneUserPassword INICIANDO | userId={}", userId);
        
        publicSchemaUnitOfWork.tx(() -> {
            Actor actor = resolveActorOrNull();

            if (userId == null) throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatório", 400);
            if (request == null) throw new ApiException(ApiErrorCode.INVALID_REQUEST, "request é obrigatório", 400);

            if (request.newPassword() == null || request.confirmPassword() == null) {
                throw new ApiException(ApiErrorCode.INVALID_PASSWORD, "Senha e confirmação são obrigatórias", 400);
            }
            if (!request.newPassword().equals(request.confirmPassword())) {
                throw new ApiException(ApiErrorCode.PASSWORD_MISMATCH, "Nova senha e confirmação não conferem", 400);
            }

            Account cp = getControlPlaneAccount();

            ControlPlaneUser user = controlPlaneUserRepository
                    .findNotDeletedByIdAndAccountId(userId, cp.getId())
                    .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário não encontrado", 404));

            if (user.isBuiltInUser()) {
                throw new ApiException(ApiErrorCode.USER_BUILT_IN_IMMUTABLE, BUILTIN_IMMUTABLE_MESSAGE, 409);
            }

            AuditTarget target = new AuditTarget(user.getEmail(), user.getId());

            Map<String, Object> attempt = m(
                    "scope", SCOPE,
                    "reason", "admin_reset"
            );

            auditAttemptSuccessFail(
                    SecurityAuditActionType.PASSWORD_RESET_COMPLETED,
                    actor,
                    target,
                    cp.getId(),
                    null,
                    attempt,
                    () -> attempt,
                    () -> {
                        String hash = passwordEncoder.encode(request.newPassword());
                        user.setTemporaryPasswordHash(hash);
                        controlPlaneUserRepository.save(user);
                        return null;
                    }
            );

            log.info("✅ resetControlPlaneUserPassword CONCLUÍDO | userId={}", userId);
            return null;
        });
    }

    public void softDeleteControlPlaneUser(Long userId) {
        log.info("softDeleteControlPlaneUser INICIANDO | userId={}", userId);
        
        publicSchemaUnitOfWork.tx(() -> {
            Actor actor = resolveActorOrNull();

            if (userId == null) throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatório", 400);

            Account cp = getControlPlaneAccount();

            ControlPlaneUser user = controlPlaneUserRepository
                    .findNotDeletedByIdAndAccountId(userId, cp.getId())
                    .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário não encontrado", 404));

            if (user.isBuiltInUser()) {
                throw new ApiException(ApiErrorCode.USER_BUILT_IN_IMMUTABLE, BUILTIN_IMMUTABLE_MESSAGE, 409);
            }

            final Long userIdForDelete = user.getId();

            AuditTarget target = new AuditTarget(user.getEmail(), user.getId());

            Map<String, Object> attempt = m(
                    "scope", SCOPE,
                    "reason", "soft_delete"
            );

            auditAttemptSuccessFail(
                    SecurityAuditActionType.USER_SOFT_DELETED,
                    actor,
                    target,
                    cp.getId(),
                    null,
                    attempt,
                    () -> attempt,
                    () -> {
                        user.softDelete();
                        controlPlaneUserRepository.save(user);
                        
                        // ✅ Remove identidade de forma SÍNCRONA (best-effort)
                        deleteControlPlaneIdentityNow(userIdForDelete, "softDelete");
                        
                        return null;
                    }
            );

            log.info("✅ softDeleteControlPlaneUser CONCLUÍDO | userId={}", userId);
            return null;
        });
    }

    public void suspendControlPlaneUserByAdmin(Long userId, ControlPlaneUserSuspendRequest request) {
        log.info("suspendControlPlaneUserByAdmin INICIANDO | userId={}", userId);
        
        publicSchemaUnitOfWork.tx(() -> {
            Actor actor = resolveActorOrNull();

            if (userId == null) throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatório", 400);

            Account cp = getControlPlaneAccount();

            ControlPlaneUser user = controlPlaneUserRepository
                    .findNotDeletedByIdAndAccountId(userId, cp.getId())
                    .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário não encontrado", 404));

            if (user.isBuiltInUser()) {
                throw new ApiException(ApiErrorCode.USER_BUILT_IN_IMMUTABLE, BUILTIN_IMMUTABLE_MESSAGE, 409);
            }

            AuditTarget target = new AuditTarget(user.getEmail(), user.getId());

            Map<String, Object> attempt = m(
                    "scope", SCOPE,
                    "reason", request == null ? null : request.reason(),
                    "by", "admin",
                    "suspendedByAdminBefore", user.isSuspendedByAdmin(),
                    "suspendedByAccount", user.isSuspendedByAccount()
            );

            Map<String, Object> success = new LinkedHashMap<>(attempt);

            auditAttemptSuccessFail(
                    SecurityAuditActionType.USER_SUSPENDED,
                    actor,
                    target,
                    cp.getId(),
                    null,
                    attempt,
                    () -> success,
                    () -> {
                        user.suspendByAdmin();
                        controlPlaneUserRepository.save(user);

                        success.put("suspendedByAdminAfter", user.isSuspendedByAdmin());
                        success.put("enabledAfter", user.isEnabled());
                        return null;
                    }
            );

            log.info("✅ suspendControlPlaneUserByAdmin CONCLUÍDO | userId={}", userId);
            return null;
        });
    }

    public void restoreControlPlaneUserByAdmin(Long userId, ControlPlaneUserSuspendRequest request) {
        log.info("restoreControlPlaneUserByAdmin INICIANDO | userId={}", userId);
        
        publicSchemaUnitOfWork.tx(() -> {
            Actor actor = resolveActorOrNull();

            if (userId == null) throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatório", 400);

            Account cp = getControlPlaneAccount();

            ControlPlaneUser user = controlPlaneUserRepository
                    .findNotDeletedByIdAndAccountId(userId, cp.getId())
                    .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário não encontrado", 404));

            if (user.isBuiltInUser()) {
                throw new ApiException(ApiErrorCode.USER_BUILT_IN_IMMUTABLE, BUILTIN_IMMUTABLE_MESSAGE, 409);
            }

            AuditTarget target = new AuditTarget(user.getEmail(), user.getId());

            Map<String, Object> attempt = m(
                    "scope", SCOPE,
                    "reason", request == null ? null : request.reason(),
                    "by", "admin",
                    "suspendedByAdminBefore", user.isSuspendedByAdmin(),
                    "suspendedByAccount", user.isSuspendedByAccount()
            );

            Map<String, Object> success = new LinkedHashMap<>(attempt);

            auditAttemptSuccessFail(
                    SecurityAuditActionType.USER_RESTORED,
                    actor,
                    target,
                    cp.getId(),
                    null,
                    attempt,
                    () -> success,
                    () -> {
                        user.unsuspendByAdmin();
                        controlPlaneUserRepository.save(user);

                        success.put("suspendedByAdminAfter", user.isSuspendedByAdmin());
                        success.put("enabledAfter", user.isEnabled());
                        return null;
                    }
            );

            log.info("✅ restoreControlPlaneUserByAdmin CONCLUÍDO | userId={}", userId);
            return null;
        });
    }

    public List<ControlPlaneUserDetailsResponse> listEnabledControlPlaneUsers() {
        log.debug("listEnabledControlPlaneUsers chamado");
        return publicSchemaUnitOfWork.readOnly(() -> {
            Account cp = getControlPlaneAccount();
            List<ControlPlaneUser> users = controlPlaneUserRepository.findEnabledByAccountId(cp.getId());
            return users.stream().map(this::mapToResponse).toList();
        });
    }

    public ControlPlaneUserDetailsResponse getEnabledControlPlaneUser(Long userId) {
        log.debug("getEnabledControlPlaneUser chamado | userId={}", userId);
        return publicSchemaUnitOfWork.readOnly(() -> {
            if (userId == null) throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatório", 400);

            Account cp = getControlPlaneAccount();

            ControlPlaneUser user = controlPlaneUserRepository
                    .findEnabledByIdAndAccountId(userId, cp.getId())
                    .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_ENABLED, "Usuário não encontrado ou não habilitado", 404));

            return mapToResponse(user);
        });
    }

    public ControlPlaneMeResponse getMe() {
        log.debug("getMe chamado");
        return publicSchemaUnitOfWork.readOnly(() -> {
            Long userId = requestIdentity.getCurrentUserId();

            Account cp = getControlPlaneAccount();

            ControlPlaneUser user = controlPlaneUserRepository.findById(userId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário não encontrado", 404));

            if (user.getAccount() == null || !cp.getId().equals(user.getAccount().getId())) {
                throw new ApiException(ApiErrorCode.FORBIDDEN, "Usuário não pertence ao Control Plane", 403);
            }

            return new ControlPlaneMeResponse(
                    user.getId(),
                    user.getAccount().getId(),
                    user.getName(),
                    user.getEmail(),
                    SystemRoleName.fromString(user.getRole() == null ? null : user.getRole().name()),
                    user.isSuspendedByAccount(),
                    user.isSuspendedByAdmin(),
                    user.isDeleted(),
                    user.isEnabled()
            );
        });
    }

    public void changeMyPassword(ControlPlaneChangeMyPasswordRequest request) {
        log.info("changeMyPassword INICIANDO");
        
        publicSchemaUnitOfWork.tx(() -> {
            Actor actor = resolveActorOrNull();

            if (request == null) throw new ApiException(ApiErrorCode.INVALID_REQUEST, "request é obrigatório", 400);
            if (request.currentPassword() == null || request.newPassword() == null || request.confirmPassword() == null) {
                throw new ApiException(ApiErrorCode.INVALID_PASSWORD, "Senha atual, nova senha e confirmação são obrigatórias", 400);
            }
            if (!request.newPassword().equals(request.confirmPassword())) {
                throw new ApiException(ApiErrorCode.PASSWORD_MISMATCH, "Nova senha e confirmação não conferem", 400);
            }

            Long accountId = requestIdentity.getCurrentAccountId();
            Long userId = requestIdentity.getCurrentUserId();

            Account cp = getControlPlaneAccount();
            if (accountId == null || userId == null || !cp.getId().equals(accountId)) {
                throw new ApiException(ApiErrorCode.FORBIDDEN, "Usuário não pertence ao Control Plane", 403);
            }

            ControlPlaneUser user = controlPlaneUserRepository.findById(userId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário não encontrado", 404));

            if (user.getAccount() == null || user.getAccount().getId() == null || !user.getAccount().getId().equals(cp.getId())) {
                throw new ApiException(ApiErrorCode.FORBIDDEN, "Usuário não pertence ao Control Plane", 403);
            }

            if (!user.isEnabled()) {
                throw new ApiException(ApiErrorCode.USER_NOT_ENABLED, "Usuário não está habilitado para trocar senha", 403);
            }

            AuditTarget target = new AuditTarget(user.getEmail(), user.getId());

            Map<String, Object> attempt = m(
                    "scope", SCOPE,
                    "reason", "self_change"
            );

            auditAttemptSuccessFail(
                    SecurityAuditActionType.PASSWORD_CHANGED,
                    actor,
                    target,
                    cp.getId(),
                    null,
                    attempt,
                    () -> attempt,
                    () -> {
                        String currentHash = user.getPassword();
                        if (currentHash == null || !passwordEncoder.matches(request.currentPassword(), currentHash)) {
                            throw new ApiException(ApiErrorCode.CURRENT_PASSWORD_INVALID, "Senha atual inválida", 400);
                        }

                        String newHash = passwordEncoder.encode(request.newPassword());
                        user.changePasswordHash(newHash, appClock.instant());

                        controlPlaneUserRepository.save(user);
                        return null;
                    }
            );

            log.info("✅ changeMyPassword CONCLUÍDO");
            return null;
        });
    }

    // =========================================================
    // Helpers (mantidos como estavam)
    // =========================================================

    private static String normalizeEmailOrThrow(String raw) {
        String email = EmailNormalizer.normalizeOrNull(raw);
        if (email == null) throw new ApiException(ApiErrorCode.INVALID_EMAIL, "Email inválido", 400);
        return email;
    }

    private static String normalizeNameOrThrow(String raw) {
        if (raw == null) throw new ApiException(ApiErrorCode.INVALID_NAME, "Nome é obrigatório", 400);
        String name = raw.trim();
        if (name.isBlank()) throw new ApiException(ApiErrorCode.INVALID_NAME, "Nome é obrigatório", 400);
        return name;
    }

    private ControlPlaneUserDetailsResponse mapToResponse(ControlPlaneUser user) {
        return new ControlPlaneUserDetailsResponse(
                user.getId(),
                user.getAccount().getId(),
                user.getName(),
                user.getEmail(),
                SystemRoleName.fromString(user.getRole() == null ? null : user.getRole().name()),
                user.isSuspendedByAccount(),
                user.isSuspendedByAdmin(),
                user.isDeleted(),
                user.isEnabled(),
                user.getAudit() == null ? null : user.getAudit().getCreatedAt()
        );
    }

    private Account getControlPlaneAccount() {
        try {
            return accountRepository.getSingleControlPlaneAccount();
        } catch (IllegalStateException e) {
            throw new ApiException(
                    ApiErrorCode.CONTROLPLANE_ACCOUNT_INVALID,
                    MSG_CP_ACCOUNT_INVALID + " " + e.getMessage(),
                    500
            );
        }
    }

    // =========================================================
    // Audit helpers (mantidos como estavam)
    // =========================================================

    @FunctionalInterface
    private interface AuditCallable<T> {
        T call();
    }

    private <T> T auditAttemptSuccessFail(
            SecurityAuditActionType actionType,
            Actor actor,
            AuditTarget target,
            Long accountId,
            String tenantSchema,
            Map<String, Object> attemptDetails,
            Supplier<Object> successDetailsSupplier,
            AuditCallable<T> block
    ) {
        recordAudit(actionType, AuditOutcome.ATTEMPT, actor, target.email(), target.userId(), accountId, tenantSchema, attemptDetails);

        try {
            T result = block.call();

            Object successDetails;
            try {
                successDetails = (successDetailsSupplier == null) ? attemptDetails : successDetailsSupplier.get();
            } catch (Exception ignored) {
                successDetails = attemptDetails;
            }
            if (successDetails == null) successDetails = attemptDetails;

            recordAudit(actionType, AuditOutcome.SUCCESS, actor, target.email(), target.userId(), accountId, tenantSchema, successDetails);
            return result;

        } catch (ApiException ex) {
            recordAudit(actionType, outcomeFrom(ex), actor, target.email(), target.userId(), accountId, tenantSchema, failureDetails(SCOPE, ex));
            throw ex;
        } catch (Exception ex) {
            recordAudit(actionType, AuditOutcome.FAILURE, actor, target.email(), target.userId(), accountId, tenantSchema, unexpectedFailureDetails(SCOPE, ex));
            throw ex;
        }
    }

    private Actor resolveActorOrNull() {
        try {
            Long actorId = requestIdentity.getCurrentUserId();
            Long accountId = requestIdentity.getCurrentAccountId();
            if (actorId == null || accountId == null) return Actor.anonymous();

            return publicSchemaUnitOfWork.readOnly(() -> controlPlaneUserRepository.findById(actorId)
                    .map(u -> new Actor(actorId, u.getEmail()))
                    .orElse(new Actor(actorId, null)));
        } catch (Exception ignored) {
            return Actor.anonymous();
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
        securityAuditService.record(
                actionType,
                outcome,
                actor == null ? null : actor.email(),
                actor == null ? null : actor.userId(),
                targetEmail,
                targetUserId,
                accountId,
                tenantSchema,
                toJson(details)
        );
    }

    private String toJson(Object details) {
        if (details == null) return null;

        JsonNode node = jsonDetailsMapper.toJsonNode(details);
        if (node == null || node.isNull()) return null;

        return node.toString();
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

    private record AuditTarget(String email, Long userId) {}
}