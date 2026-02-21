package brito.com.multitenancy001.controlplane.users.app;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.controlplane.security.ControlPlaneRole;
import brito.com.multitenancy001.controlplane.users.api.dto.*;
import brito.com.multitenancy001.controlplane.users.domain.ControlPlaneBuiltInUsers;
import brito.com.multitenancy001.controlplane.users.domain.ControlPlaneUser;
import brito.com.multitenancy001.controlplane.users.persistence.ControlPlaneUserRepository;
import brito.com.multitenancy001.integration.security.ControlPlaneRequestIdentityService;
import brito.com.multitenancy001.infrastructure.publicschema.audit.SecurityAuditService;
import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;
import brito.com.multitenancy001.shared.domain.common.EntityOrigin;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.json.JsonDetailsMapper;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.persistence.publicschema.LoginIdentityProvisioningService;
import brito.com.multitenancy001.shared.security.SystemRoleName;
import brito.com.multitenancy001.shared.time.AppClock;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Use cases de usuários do Control Plane.
 *
 * Objetivos:
 * - CRUD administrativo (create/update/permissions/reset password/soft delete/restore).
 * - Endpoints "enabled" (somente usuários habilitados).
 * - Endpoint "me" e troca de senha do próprio usuário.
 *
 * Auditoria SOC2-like:
 * - Trilho append-only com ATTEMPT + SUCCESS/FAILURE/DENIED.
 * - Details estruturado (scope, motivo, alvo, mudanças/deltas).
 * - IP/UA/requestId são capturados pelo SecurityAuditService via RequestMetaContext.
 *
 * Regras:
 * - Usuário BUILT_IN é protegido: não altera/deleta/restaura; apenas troca de senha.
 * - Todas as operações são restritas ao account "Control Plane" (single CP account).
 */
@Service
@RequiredArgsConstructor
public class ControlPlaneUserService {

    private static final String BUILTIN_IMMUTABLE_MESSAGE =
            "Usuário BUILT_IN é protegido: não pode ser alterado/deletado/restaurado; apenas senha pode ser trocada.";

    private static final String MSG_CP_ACCOUNT_INVALID =
            "Configuração inválida: conta do Control Plane ausente ou duplicada.";

    private static final String SCOPE = "CONTROL_PLANE";

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final AccountRepository accountRepository;
    private final ControlPlaneUserRepository controlPlaneUserRepository;

    /** Identidade do request (actor). */
    private final ControlPlaneRequestIdentityService requestIdentity;

    private final ControlPlaneUserExplicitPermissionsService explicitPermissionsService;

    private final PasswordEncoder passwordEncoder;
    private final AppClock appClock;

    private final LoginIdentityProvisioningService loginIdentityProvisioningService;

    /** Auditoria real (public schema) com RequestMetaContext. */
    private final SecurityAuditService securityAuditService;

    /** Mapper para details estruturado (Map/record -> JsonNode). */
    private final JsonDetailsMapper jsonDetailsMapper;

    // =========================================================
    // ADMIN ENDPOINTS
    // =========================================================

    public ControlPlaneUserDetailsResponse createControlPlaneUser(ControlPlaneUserCreateRequest request) {
        /* Cria um novo usuário do Control Plane (ADMIN). */
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

            return auditAttemptSuccessFail(
                    SecurityAuditActionType.USER_CREATED,
                    actor,
                    target,
                    cp.getId(),
                    null,
                    attempt,
                    null,
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

                            // SOC2-like: permissions changed no create
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

                        loginIdentityProvisioningService.ensureControlPlaneIdentity(email, saved.getId());

                        // SUCCESS do create (com dados finais)
                        recordAudit(
                                SecurityAuditActionType.USER_CREATED,
                                AuditOutcome.SUCCESS,
                                actor,
                                saved.getEmail(),
                                saved.getId(),
                                cp.getId(),
                                null,
                                m(
                                        "scope", SCOPE,
                                        "stage", "after_save",
                                        "role", role == null ? null : role.name(),
                                        "permissionsCount", request.permissions() == null ? 0 : request.permissions().size()
                                )
                        );

                        return getControlPlaneUser(saved.getId());
                    }
            );
        });
    }

    public List<ControlPlaneUserDetailsResponse> listControlPlaneUsers() {
        /* Lista usuários (não deletados) do Control Plane. */
        return publicSchemaUnitOfWork.readOnly(() -> {
            Account cp = getControlPlaneAccount();
            return controlPlaneUserRepository.findNotDeletedByAccountId(cp.getId()).stream()
                    .map(this::mapToResponse)
                    .toList();
        });
    }

    public ControlPlaneUserDetailsResponse getControlPlaneUser(Long userId) {
        /* Busca usuário do Control Plane por id. */
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

    public ControlPlaneUserDetailsResponse updateControlPlaneUser(Long userId, ControlPlaneUserUpdateRequest request) {
        /* Atualiza dados do usuário do Control Plane (ADMIN). */
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

            return auditAttemptSuccessFail(
                    SecurityAuditActionType.USER_UPDATED,
                    actor,
                    target,
                    cp.getId(),
                    null,
                    attempt,
                    null,
                    () -> {
                        boolean anyChange = false;
                        boolean roleChanged = false;

                        Map<String, Object> changes = new LinkedHashMap<>();

                        if (request.name() != null) {
                            String newName = normalizeNameOrThrow(request.name());
                            user.rename(newName);
                            anyChange = true;
                            changes.put("name", "changed");
                        }

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
                                loginIdentityProvisioningService.moveControlPlaneIdentity(user.getId(), newEmail);

                                anyChange = true;
                                changes.put("emailBefore", beforeEmail);
                                changes.put("emailAfter", newEmail);
                            }
                        }

                        if (request.role() != null) {
                            if (beforeRole == null || !beforeRole.equals(request.role())) {
                                user.changeRole(request.role());
                                anyChange = true;
                                roleChanged = true;
                            }
                        }

                        controlPlaneUserRepository.save(user);

                        // Permissões explícitas: neste endpoint você aplica o conjunto recebido.
                        // (Delta real exigiria ler as permissões atuais antes; aqui ao menos registramos o payload.)
                        if (request.permissions() != null) {
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
                                            "permissionsCount", request.permissions().size(),
                                            "permissions", request.permissions()
                                    )
                            );
                        }

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

                        // SUCCESS do update com changes (safe)
                        recordAudit(
                                SecurityAuditActionType.USER_UPDATED,
                                AuditOutcome.SUCCESS,
                                actor,
                                user.getEmail(),
                                user.getId(),
                                cp.getId(),
                                null,
                                m(
                                        "scope", SCOPE,
                                        "changed", anyChange,
                                        "changes", changes
                                )
                        );

                        return getControlPlaneUser(userId);
                    }
            );
        });
    }

    public ControlPlaneUserDetailsResponse updateControlPlaneUserPermissions(Long userId, ControlPlaneUserPermissionsUpdateRequest request) {
        /* Atualiza permissões explícitas (ADMIN endpoint dedicado). */
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

            return auditAttemptSuccessFail(
                    SecurityAuditActionType.PERMISSIONS_CHANGED,
                    actor,
                    target,
                    cp.getId(),
                    null,
                    attempt,
                    null,
                    () -> {
                        explicitPermissionsService.setExplicitPermissionsFromCodes(userId, request.permissions());

                        recordAudit(
                                SecurityAuditActionType.PERMISSIONS_CHANGED,
                                AuditOutcome.SUCCESS,
                                actor,
                                targetUser.getEmail(),
                                targetUser.getId(),
                                cp.getId(),
                                null,
                                m(
                                        "scope", SCOPE,
                                        "reason", "permissions_endpoint",
                                        "permissionsCount", permCount,
                                        "permissions", request.permissions()
                                )
                        );

                        return getControlPlaneUser(userId);
                    }
            );
        });
    }

    public void resetControlPlaneUserPassword(Long userId, ControlPlaneUserPasswordResetRequest request) {
        /* Admin reset de senha (não é troca autenticada). */
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
                    attempt,
                    () -> {
                        String hash = passwordEncoder.encode(request.newPassword());
                        user.setTemporaryPasswordHash(hash);
                        controlPlaneUserRepository.save(user);
                        return null;
                    }
            );

            return null;
        });
    }

    public void softDeleteControlPlaneUser(Long userId) {
        /* Soft delete (ADMIN). */
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
                    "reason", "soft_delete"
            );

            auditAttemptSuccessFail(
                    SecurityAuditActionType.USER_SOFT_DELETED,
                    actor,
                    target,
                    cp.getId(),
                    null,
                    attempt,
                    attempt,
                    () -> {
                        user.softDelete();
                        controlPlaneUserRepository.save(user);
                        return null;
                    }
            );

            return null;
        });
    }

    public ControlPlaneUserDetailsResponse restoreControlPlaneUser(Long userId) {
        /* Restaura usuário após soft delete (ADMIN). */
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

            return auditAttemptSuccessFail(
                    SecurityAuditActionType.USER_SOFT_RESTORED,
                    actor,
                    target,
                    cp.getId(),
                    null,
                    attempt,
                    attempt,
                    () -> {
                        user.restore();
                        controlPlaneUserRepository.save(user);

                        loginIdentityProvisioningService.ensureControlPlaneIdentity(user.getEmail(), user.getId());

                        return mapToResponse(user);
                    }
            );
        });
    }

    // =========================================================
    // ENABLED ENDPOINTS
    // =========================================================

    public List<ControlPlaneUserDetailsResponse> listEnabledControlPlaneUsers() {
        /* Lista usuários habilitados do Control Plane. */
        return publicSchemaUnitOfWork.readOnly(() -> {
            Account cp = getControlPlaneAccount();

            List<ControlPlaneUser> users = controlPlaneUserRepository.findEnabledByAccountId(cp.getId());

            return users.stream()
                    .map(this::mapToResponse)
                    .toList();
        });
    }

    public ControlPlaneUserDetailsResponse getEnabledControlPlaneUser(Long userId) {
        /* Busca usuário habilitado do Control Plane. */
        return publicSchemaUnitOfWork.readOnly(() -> {
            if (userId == null) throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatório", 400);

            Account cp = getControlPlaneAccount();

            ControlPlaneUser user = controlPlaneUserRepository
                    .findEnabledByIdAndAccountId(userId, cp.getId())
                    .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_ENABLED, "Usuário não encontrado ou não habilitado", 404));

            return mapToResponse(user);
        });
    }

    // =========================================================
    // ME
    // =========================================================

    public ControlPlaneMeResponse getMe() {
        /* Retorna informações do usuário autenticado no Control Plane. */
        return publicSchemaUnitOfWork.readOnly(() -> {
            Long accountId = requestIdentity.getCurrentAccountId();
            Long userId = requestIdentity.getCurrentUserId();

            Account cp = getControlPlaneAccount();
            if (accountId == null || !cp.getId().equals(accountId)) {
                throw new ApiException(ApiErrorCode.FORBIDDEN, "Usuário não pertence ao Control Plane", 403);
            }

            ControlPlaneUser user = controlPlaneUserRepository.findById(userId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário não encontrado", 404));

            if (user.getAccount() == null || user.getAccount().getId() == null || !user.getAccount().getId().equals(cp.getId())) {
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
        /* Troca de senha autenticada (self). */
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
                    attempt,
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

            return null;
        });
    }

    // =========================================================
    // Helpers
    // =========================================================

    private static String normalizeEmailOrThrow(String raw) {
        /* Normaliza e valida email. */
        String email = EmailNormalizer.normalizeOrNull(raw);
        if (email == null) throw new ApiException(ApiErrorCode.INVALID_EMAIL, "Email inválido", 400);
        return email;
    }

    private static String normalizeNameOrThrow(String raw) {
        /* Normaliza e valida nome. */
        if (raw == null) throw new ApiException(ApiErrorCode.INVALID_NAME, "Nome é obrigatório", 400);
        String name = raw.trim();
        if (name.isBlank()) throw new ApiException(ApiErrorCode.INVALID_NAME, "Nome é obrigatório", 400);
        return name;
    }

    private ControlPlaneUserDetailsResponse mapToResponse(ControlPlaneUser user) {
        /* Mapeia entidade para response DTO. */
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
        /* Busca a conta única do Control Plane (fail-fast se inválida). */
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
    // Audit helper (ATTEMPT + SUCCESS/FAIL/DENIED)
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
            Map<String, Object> successDetails,
            AuditCallable<T> block
    ) {
        /* Padroniza trilha ATTEMPT + SUCCESS/FAIL/DENIED para um caso de uso. */
        recordAudit(actionType, AuditOutcome.ATTEMPT, actor, target.email(), target.userId(), accountId, tenantSchema, attemptDetails);

        try {
            T result = block.call();

            // Se successDetails for nulo, reaproveita o attempt (comum em operações simples).
            Map<String, Object> sd = (successDetails != null ? successDetails : attemptDetails);
            recordAudit(actionType, AuditOutcome.SUCCESS, actor, target.email(), target.userId(), accountId, tenantSchema, sd);

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
        /* Resolve o actor do request (best-effort), sem quebrar o fluxo em caso de erro. */
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
            Map<String, Object> details
    ) {
        /* Grava evento de auditoria com details estruturado. */
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
        /* Serializa details (Map/record/String) para JSON string compatível com detailsJson do evento. */
        if (details == null) return null;

        // Compat: se já vier String, o JsonDetailsMapper converte para JsonNode (json válido vira node, texto vira string json).
        JsonNode node = jsonDetailsMapper.toJsonNode(details);
        if (node == null || node.isNull()) return null;

        return node.toString();
    }

    private static AuditOutcome outcomeFrom(ApiException ex) {
        /* Mapeia ApiException para outcome semântico (DENIED vs FAILURE). */
        if (ex == null) return AuditOutcome.FAILURE;
        int s = ex.getStatus();
        return (s == 401 || s == 403) ? AuditOutcome.DENIED : AuditOutcome.FAILURE;
    }

    private static Map<String, Object> failureDetails(String scope, ApiException ex) {
        /* Details estruturado para falhas de negócio/validação. */
        return m(
                "scope", scope,
                "error", ex == null ? null : ex.getError(),
                "status", ex == null ? 0 : ex.getStatus(),
                "message", ex == null ? null : safeMessage(ex.getMessage())
        );
    }

    private static Map<String, Object> unexpectedFailureDetails(String scope, Exception ex) {
        /* Details estruturado para falhas inesperadas (sem vazar stacktrace/segredo). */
        return m(
                "scope", scope,
                "unexpected", ex == null ? null : ex.getClass().getSimpleName(),
                "message", ex == null ? null : safeMessage(ex.getMessage())
        );
    }

    private static String safeMessage(String msg) {
        /* Sanitiza mensagem para evitar quebras/ruído no JSON. */
        if (!StringUtils.hasText(msg)) return null;
        return msg
                .replace("\n", " ")
                .replace("\r", " ")
                .replace("\t", " ")
                .trim();
    }

    private static Map<String, Object> m(Object... kv) {
        /* Cria Map ordenado para JSON estável e legível. */
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
            /* Actor "desconhecido" (não quebra o fluxo). */
            return new Actor(null, null);
        }
    }

    private record AuditTarget(String email, Long userId) {}
}