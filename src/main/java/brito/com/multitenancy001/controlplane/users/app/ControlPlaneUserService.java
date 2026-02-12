package brito.com.multitenancy001.controlplane.users.app;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.controlplane.security.ControlPlaneRole;
import brito.com.multitenancy001.controlplane.users.api.dto.*;
import brito.com.multitenancy001.controlplane.users.domain.ControlPlaneBuiltInUsers;
import brito.com.multitenancy001.controlplane.users.domain.ControlPlaneUser;
import brito.com.multitenancy001.controlplane.users.persistence.ControlPlaneUserRepository;
import brito.com.multitenancy001.infrastructure.publicschema.audit.SecurityAuditService;
import brito.com.multitenancy001.infrastructure.security.SecurityUtils;
import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;
import brito.com.multitenancy001.shared.domain.common.EntityOrigin;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.persistence.publicschema.LoginIdentityProvisioningService;
import brito.com.multitenancy001.shared.security.SystemRoleName;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ControlPlaneUserService {

    private static final String BUILTIN_IMMUTABLE_CODE = "USER_BUILT_IN_IMMUTABLE";
    private static final String BUILTIN_IMMUTABLE_MESSAGE =
            "Usuário BUILT_IN é protegido: não pode ser alterado/deletado/restaurado; apenas senha pode ser trocada.";

    private static final String CP_ACCOUNT_INVALID_CODE = "CONTROLPLANE_ACCOUNT_INVALID";
    private static final String CP_ACCOUNT_INVALID_MESSAGE =
            "Configuração inválida: conta do Control Plane ausente ou duplicada.";

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final AccountRepository accountRepository;
    private final ControlPlaneUserRepository controlPlaneUserRepository;

    private final SecurityUtils securityUtils;
    private final ControlPlaneUserExplicitPermissionsService explicitPermissionsService;

    private final PasswordEncoder passwordEncoder;
    private final AppClock appClock;

    private final LoginIdentityProvisioningService loginIdentityProvisioningService;

    // ✅ append-only security audit (public schema; geralmente REQUIRES_NEW por dentro)
    private final SecurityAuditService securityAuditService;

    // =========================================================
    // ADMIN ENDPOINTS
    // =========================================================

    public ControlPlaneUserDetailsResponse createControlPlaneUser(ControlPlaneUserCreateRequest request) {
        return publicSchemaUnitOfWork.tx(() -> {
            Actor actor = resolveActorOrNull();

            if (request == null) throw new ApiException("INVALID_REQUEST", "request é obrigatório", 400);
            if (request.role() == null) throw new ApiException("ROLE_REQUIRED", "role é obrigatório", 400);
            if (request.password() == null || request.password().isBlank()) {
                throw new ApiException("INVALID_PASSWORD", "senha é obrigatória", 400);
            }

            Account cp = getControlPlaneAccount();

            String email = normalizeEmailOrThrow(request.email());
            if (ControlPlaneBuiltInUsers.isReservedEmail(email)) {
                throw new ApiException("EMAIL_RESERVED", "Este email é reservado do sistema (BUILT_IN)", 409);
            }

            // target ainda não existe (id null)
            AuditTarget target = new AuditTarget(email, null);

            return auditAttemptSuccessFail(
                    SecurityAuditActionType.USER_CREATED,
                    actor,
                    target,
                    cp.getId(),
                    null,
                    "{\"scope\":\"CONTROL_PLANE\",\"stage\":\"before_save\"}",
                    "{\"scope\":\"CONTROL_PLANE\",\"stage\":\"after_save\"}",
                    () -> {
                        boolean emailExists = controlPlaneUserRepository
                                .findByEmailAndAccount_IdAndDeletedFalse(email, cp.getId())
                                .isPresent();
                        if (emailExists) {
                            throw new ApiException("EMAIL_ALREADY_IN_USE", "Já existe um usuário ativo com este email", 409);
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

                            // perms changed (create)
                            recordAudit(
                                    SecurityAuditActionType.PERMISSIONS_CHANGED,
                                    AuditOutcome.SUCCESS,
                                    actor,
                                    saved.getEmail(),
                                    saved.getId(),
                                    cp.getId(),
                                    null,
                                    "{\"scope\":\"CONTROL_PLANE\",\"reason\":\"create\",\"permissionsCount\":" + request.permissions().size() + "}"
                            );
                        }

                        loginIdentityProvisioningService.ensureControlPlaneIdentity(email, saved.getId());

                        // log SUCCESS do create com role (sem duplicar o bloco inteiro)
                        recordAudit(
                                SecurityAuditActionType.USER_CREATED,
                                AuditOutcome.SUCCESS,
                                actor,
                                saved.getEmail(),
                                saved.getId(),
                                cp.getId(),
                                null,
                                "{"
                                        + "\"scope\":\"CONTROL_PLANE\""
                                        + ",\"stage\":\"after_save\""
                                        + ",\"role\":\"" + jsonEscape(role.name()) + "\""
                                        + "}"
                        );

                        return getControlPlaneUser(saved.getId());
                    }
            );
        });
    }

    public List<ControlPlaneUserDetailsResponse> listControlPlaneUsers() {
        return publicSchemaUnitOfWork.readOnly(() -> {
            Account cp = getControlPlaneAccount();
            return controlPlaneUserRepository.findNotDeletedByAccountId(cp.getId()).stream()
                    .map(this::mapToResponse)
                    .toList();
        });
    }

    public ControlPlaneUserDetailsResponse getControlPlaneUser(Long userId) {
        return publicSchemaUnitOfWork.readOnly(() -> {
            if (userId == null) throw new ApiException("USER_ID_REQUIRED", "userId é obrigatório", 400);

            Account cp = getControlPlaneAccount();

            ControlPlaneUser user = controlPlaneUserRepository.findById(userId)
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

            if (user.getAccount() == null || user.getAccount().getId() == null || !user.getAccount().getId().equals(cp.getId())) {
                throw new ApiException("USER_OUT_OF_SCOPE", "Usuário não pertence ao Control Plane", 403);
            }

            return mapToResponse(user);
        });
    }

    public ControlPlaneUserDetailsResponse updateControlPlaneUser(Long userId, ControlPlaneUserUpdateRequest request) {
        return publicSchemaUnitOfWork.tx(() -> {
            Actor actor = resolveActorOrNull();

            if (userId == null) throw new ApiException("USER_ID_REQUIRED", "userId é obrigatório", 400);
            if (request == null) throw new ApiException("INVALID_REQUEST", "request é obrigatório", 400);

            Account cp = getControlPlaneAccount();

            ControlPlaneUser user = controlPlaneUserRepository
                    .findNotDeletedByIdAndAccountId(userId, cp.getId())
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

            if (user.isBuiltInUser()) {
                throw new ApiException(BUILTIN_IMMUTABLE_CODE, BUILTIN_IMMUTABLE_MESSAGE, 409);
            }

            String beforeEmail = user.getEmail();
            ControlPlaneRole beforeRole = user.getRole();

            AuditTarget target = new AuditTarget(user.getEmail(), user.getId());

            return auditAttemptSuccessFail(
                    SecurityAuditActionType.USER_UPDATED,
                    actor,
                    target,
                    cp.getId(),
                    null,
                    "{\"scope\":\"CONTROL_PLANE\"}",
                    null,
                    () -> {
                        boolean anyChange = false;
                        boolean roleChanged = false;

                        if (request.name() != null) {
                            user.rename(normalizeNameOrThrow(request.name()));
                            anyChange = true;
                        }

                        if (request.email() != null) {
                            String newEmail = normalizeEmailOrThrow(request.email());

                            if (ControlPlaneBuiltInUsers.isReservedEmail(newEmail)) {
                                throw new ApiException("EMAIL_RESERVED", "Este email é reservado do sistema (BUILT_IN)", 409);
                            }

                            String currentEmail = EmailNormalizer.normalizeOrNull(user.getEmail());
                            if (currentEmail == null || !currentEmail.equals(newEmail)) {

                                boolean emailExists = controlPlaneUserRepository
                                        .findByEmailAndAccount_IdAndDeletedFalse(newEmail, cp.getId())
                                        .filter(u -> !u.getId().equals(user.getId()))
                                        .isPresent();

                                if (emailExists) {
                                    throw new ApiException("EMAIL_ALREADY_IN_USE", "Já existe um usuário ativo com este email", 409);
                                }

                                user.changeEmail(newEmail);
                                loginIdentityProvisioningService.moveControlPlaneIdentity(user.getId(), newEmail);
                                anyChange = true;
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
                                    "{\"scope\":\"CONTROL_PLANE\",\"reason\":\"update\",\"permissionsCount\":" + request.permissions().size() + "}"
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
                                    "{"
                                            + "\"scope\":\"CONTROL_PLANE\""
                                            + ",\"from\":\"" + jsonEscape(beforeRole == null ? null : beforeRole.name()) + "\""
                                            + ",\"to\":\"" + jsonEscape(user.getRole() == null ? null : user.getRole().name()) + "\""
                                            + "}"
                            );
                        }

                        // SUCCESS "final" de USER_UPDATED com diffs úteis
                        recordAudit(
                                SecurityAuditActionType.USER_UPDATED,
                                AuditOutcome.SUCCESS,
                                actor,
                                user.getEmail(),
                                user.getId(),
                                cp.getId(),
                                null,
                                "{"
                                        + "\"scope\":\"CONTROL_PLANE\""
                                        + ",\"changed\":" + (anyChange ? "true" : "false")
                                        + ",\"emailBefore\":\"" + jsonEscape(beforeEmail) + "\""
                                        + ",\"emailAfter\":\"" + jsonEscape(user.getEmail()) + "\""
                                        + "}"
                        );

                        return getControlPlaneUser(userId);
                    }
            );
        });
    }

    public ControlPlaneUserDetailsResponse updateControlPlaneUserPermissions(Long userId, ControlPlaneUserPermissionsUpdateRequest request) {
        return publicSchemaUnitOfWork.tx(() -> {
            Actor actor = resolveActorOrNull();

            if (userId == null) throw new ApiException("USER_ID_REQUIRED", "userId é obrigatório", 400);
            if (request == null) throw new ApiException("INVALID_REQUEST", "request é obrigatório", 400);

            Account cp = getControlPlaneAccount();

            ControlPlaneUser targetUser = controlPlaneUserRepository.findById(userId)
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

            if (targetUser.getAccount() == null || targetUser.getAccount().getId() == null || !targetUser.getAccount().getId().equals(cp.getId())) {
                throw new ApiException("USER_OUT_OF_SCOPE", "Usuário não pertence ao Control Plane", 403);
            }

            if (targetUser.isBuiltInUser()) {
                throw new ApiException(BUILTIN_IMMUTABLE_CODE, BUILTIN_IMMUTABLE_MESSAGE, 409);
            }

            int permCount = (request.permissions() == null) ? 0 : request.permissions().size();

            AuditTarget target = new AuditTarget(targetUser.getEmail(), targetUser.getId());

            return auditAttemptSuccessFail(
                    SecurityAuditActionType.PERMISSIONS_CHANGED,
                    actor,
                    target,
                    cp.getId(),
                    null,
                    "{\"scope\":\"CONTROL_PLANE\",\"reason\":\"permissions_endpoint\",\"permissionsCount\":" + permCount + "}",
                    "{\"scope\":\"CONTROL_PLANE\",\"permissionsCount\":" + permCount + "}",
                    () -> {
                        explicitPermissionsService.setExplicitPermissionsFromCodes(userId, request.permissions());
                        return getControlPlaneUser(userId);
                    }
            );
        });
    }

    public void resetControlPlaneUserPassword(Long userId, ControlPlaneUserPasswordResetRequest request) {
        publicSchemaUnitOfWork.tx(() -> {
            Actor actor = resolveActorOrNull();

            if (userId == null) throw new ApiException("USER_ID_REQUIRED", "userId é obrigatório", 400);
            if (request == null) throw new ApiException("INVALID_REQUEST", "request é obrigatório", 400);

            if (request.newPassword() == null || request.confirmPassword() == null) {
                throw new ApiException("INVALID_PASSWORD", "Senha e confirmação são obrigatórias", 400);
            }
            if (!request.newPassword().equals(request.confirmPassword())) {
                throw new ApiException("PASSWORD_MISMATCH", "Nova senha e confirmação não conferem", 400);
            }

            Account cp = getControlPlaneAccount();

            ControlPlaneUser user = controlPlaneUserRepository
                    .findNotDeletedByIdAndAccountId(userId, cp.getId())
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

            AuditTarget target = new AuditTarget(user.getEmail(), user.getId());

            auditAttemptSuccessFail(
                    SecurityAuditActionType.PASSWORD_CHANGED,
                    actor,
                    target,
                    cp.getId(),
                    null,
                    "{\"scope\":\"CONTROL_PLANE\",\"reason\":\"admin_reset\"}",
                    "{\"scope\":\"CONTROL_PLANE\",\"reason\":\"admin_reset\"}",
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
        publicSchemaUnitOfWork.tx(() -> {
            Actor actor = resolveActorOrNull();

            if (userId == null) throw new ApiException("USER_ID_REQUIRED", "userId é obrigatório", 400);

            Account cp = getControlPlaneAccount();

            ControlPlaneUser user = controlPlaneUserRepository
                    .findNotDeletedByIdAndAccountId(userId, cp.getId())
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

            if (user.isBuiltInUser()) {
                throw new ApiException(BUILTIN_IMMUTABLE_CODE, BUILTIN_IMMUTABLE_MESSAGE, 409);
            }

            AuditTarget target = new AuditTarget(user.getEmail(), user.getId());

            auditAttemptSuccessFail(
                    SecurityAuditActionType.USER_SUSPENDED,
                    actor,
                    target,
                    cp.getId(),
                    null,
                    "{\"scope\":\"CONTROL_PLANE\",\"reason\":\"soft_delete\"}",
                    "{\"scope\":\"CONTROL_PLANE\",\"reason\":\"soft_delete\"}",
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
        return publicSchemaUnitOfWork.tx(() -> {
            Actor actor = resolveActorOrNull();

            if (userId == null) throw new ApiException("USER_ID_REQUIRED", "userId é obrigatório", 400);

            Account cp = getControlPlaneAccount();

            ControlPlaneUser user = controlPlaneUserRepository.findById(userId)
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

            if (user.getAccount() == null || user.getAccount().getId() == null || !user.getAccount().getId().equals(cp.getId())) {
                throw new ApiException("USER_OUT_OF_SCOPE", "Usuário não pertence ao Control Plane", 403);
            }

            if (user.isBuiltInUser()) {
                throw new ApiException(BUILTIN_IMMUTABLE_CODE, BUILTIN_IMMUTABLE_MESSAGE, 409);
            }

            AuditTarget target = new AuditTarget(user.getEmail(), user.getId());

            return auditAttemptSuccessFail(
                    SecurityAuditActionType.USER_RESTORED,
                    actor,
                    target,
                    cp.getId(),
                    null,
                    "{\"scope\":\"CONTROL_PLANE\"}",
                    "{\"scope\":\"CONTROL_PLANE\"}",
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
        return publicSchemaUnitOfWork.readOnly(() -> {
            Account cp = getControlPlaneAccount();

            List<ControlPlaneUser> users = controlPlaneUserRepository.findEnabledByAccountId(cp.getId());

            return users.stream()
                    .map(this::mapToResponse)
                    .toList();
        });
    }

    public ControlPlaneUserDetailsResponse getEnabledControlPlaneUser(Long userId) {
        return publicSchemaUnitOfWork.readOnly(() -> {
            if (userId == null) throw new ApiException("USER_ID_REQUIRED", "userId é obrigatório", 400);

            Account cp = getControlPlaneAccount();

            ControlPlaneUser user = controlPlaneUserRepository
                    .findEnabledByIdAndAccountId(userId, cp.getId())
                    .orElseThrow(() -> new ApiException("USER_NOT_ENABLED", "Usuário não encontrado ou não habilitado", 404));

            return mapToResponse(user);
        });
    }

    // =========================================================
    // ME
    // =========================================================

    public ControlPlaneMeResponse getMe() {
        return publicSchemaUnitOfWork.readOnly(() -> {
            Long accountId = securityUtils.getCurrentAccountId();
            Long userId = securityUtils.getCurrentUserId();

            Account cp = getControlPlaneAccount();
            if (accountId == null || !cp.getId().equals(accountId)) {
                throw new ApiException("FORBIDDEN", "Usuário não pertence ao Control Plane", 403);
            }

            ControlPlaneUser user = controlPlaneUserRepository.findById(userId)
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

            if (user.getAccount() == null || user.getAccount().getId() == null || !user.getAccount().getId().equals(cp.getId())) {
                throw new ApiException("FORBIDDEN", "Usuário não pertence ao Control Plane", 403);
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
        publicSchemaUnitOfWork.tx(() -> {
            Actor actor = resolveActorOrNull();

            if (request == null) throw new ApiException("INVALID_REQUEST", "request é obrigatório", 400);
            if (request.currentPassword() == null || request.newPassword() == null || request.confirmPassword() == null) {
                throw new ApiException("INVALID_PASSWORD", "Senha atual, nova senha e confirmação são obrigatórias", 400);
            }
            if (!request.newPassword().equals(request.confirmPassword())) {
                throw new ApiException("PASSWORD_MISMATCH", "Nova senha e confirmação não conferem", 400);
            }

            Long accountId = securityUtils.getCurrentAccountId();
            Long userId = securityUtils.getCurrentUserId();

            Account cp = getControlPlaneAccount();
            if (accountId == null || userId == null || !cp.getId().equals(accountId)) {
                throw new ApiException("FORBIDDEN", "Usuário não pertence ao Control Plane", 403);
            }

            ControlPlaneUser user = controlPlaneUserRepository.findById(userId)
                    .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

            if (user.getAccount() == null || user.getAccount().getId() == null || !user.getAccount().getId().equals(cp.getId())) {
                throw new ApiException("FORBIDDEN", "Usuário não pertence ao Control Plane", 403);
            }

            if (!user.isEnabled()) {
                throw new ApiException("USER_NOT_ENABLED", "Usuário não está habilitado para trocar senha", 403);
            }

            AuditTarget target = new AuditTarget(user.getEmail(), user.getId());

            auditAttemptSuccessFail(
                    SecurityAuditActionType.PASSWORD_CHANGED,
                    actor,
                    target,
                    cp.getId(),
                    null,
                    "{\"scope\":\"CONTROL_PLANE\",\"reason\":\"self_change\"}",
                    "{\"scope\":\"CONTROL_PLANE\",\"reason\":\"self_change\"}",
                    () -> {
                        String currentHash = user.getPassword();
                        if (currentHash == null || !passwordEncoder.matches(request.currentPassword(), currentHash)) {
                            throw new ApiException("CURRENT_PASSWORD_INVALID", "Senha atual inválida", 400);
                        }

                        String newHash = passwordEncoder.encode(request.newPassword());
                        user.changePasswordHash(newHash, appClock.instant());

                        controlPlaneUserRepository.save(user);
                        return null;
                    }
            );

            return;
        });
    }

    // =========================================================
    // Helpers
    // =========================================================

    private static String normalizeEmailOrThrow(String raw) {
        String email = EmailNormalizer.normalizeOrNull(raw);
        if (email == null) throw new ApiException("INVALID_EMAIL", "Email inválido", 400);
        return email;
    }

    private static String normalizeNameOrThrow(String raw) {
        if (raw == null) throw new ApiException("INVALID_NAME", "Nome é obrigatório", 400);
        String name = raw.trim();
        if (name.isBlank()) throw new ApiException("INVALID_NAME", "Nome é obrigatório", 400);
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
                    CP_ACCOUNT_INVALID_CODE,
                    CP_ACCOUNT_INVALID_MESSAGE + " " + e.getMessage(),
                    500
            );
        }
    }

    // =========================================================
    // Audit helper (ATTEMPT + SUCCESS/FAIL)
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
            String attemptDetailsJson,
            String successDetailsJson,
            AuditCallable<T> block
    ) {
        // ATTEMPT
        recordAudit(actionType, AuditOutcome.ATTEMPT, actor, target.email(), target.userId(), accountId, tenantSchema, attemptDetailsJson);

        try {
            T result = block.call();

            // SUCCESS
            recordAudit(actionType, AuditOutcome.SUCCESS, actor, target.email(), target.userId(), accountId, tenantSchema, successDetailsJson);

            return result;
        } catch (ApiException ex) {
            recordAudit(actionType, outcomeFrom(ex), actor, target.email(), target.userId(), accountId, tenantSchema, failureDetailsJson("CONTROL_PLANE", ex));
            throw ex;
        } catch (Exception ex) {
            recordAudit(actionType, AuditOutcome.FAILURE, actor, target.email(), target.userId(), accountId, tenantSchema, unexpectedFailureDetailsJson("CONTROL_PLANE", ex));
            throw ex;
        }
    }

    private Actor resolveActorOrNull() {
        try {
            Long actorId = securityUtils.getCurrentUserId();
            Long accountId = securityUtils.getCurrentAccountId();
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

    private record AuditTarget(String email, Long userId) {}
}
