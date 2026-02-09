package brito.com.multitenancy001.infrastructure.tenant;

import java.time.Instant;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.infrastructure.persistence.TransactionExecutor;
import brito.com.multitenancy001.shared.contracts.UserSummaryData;
import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.security.TenantRoleName;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.security.TenantRole;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import brito.com.multitenancy001.tenant.users.persistence.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantUserProvisioningFacade {

    private static final String REQUIRED_TABLE = "tenant_users";
    private static final String OWNER_NAME_FALLBACK = "Owner";

    // error codes padrão
    private static final String TENANT_OWNER_REQUIRED = "TENANT_OWNER_REQUIRED";

    private final TenantExecutor tenantExecutor;
    private final TransactionExecutor transactionExecutor;

    private final TenantUserRepository tenantUserRepository;
    private final PasswordEncoder passwordEncoder;

    private final AppClock appClock;

    public List<UserSummaryData> listUserSummaries(String tenantSchema, Long accountId, boolean onlyOperational) {
        tenantExecutor.assertTenantSchemaReadyOrThrow(tenantSchema, REQUIRED_TABLE);

        return tenantExecutor.runInTenantSchema(tenantSchema, () ->
                transactionExecutor.inTenantReadOnlyTx(() -> {

                    var users = onlyOperational
                            ? tenantUserRepository.findByAccountIdAndDeletedFalseAndSuspendedByAccountFalseAndSuspendedByAdminFalse(accountId)
                            : tenantUserRepository.findByAccountId(accountId);

                    return users.stream()
                            .map(u -> new UserSummaryData(
                                    u.getId(),
                                    u.getAccountId(),
                                    u.getName(),
                                    u.getEmail(),
                                    u.getRole() == null ? null : TenantRoleName.valueOf(u.getRole().name()),
                                    u.isSuspendedByAccount(),
                                    u.isSuspendedByAdmin(),
                                    u.isDeleted()
                            ))
                            .toList();
                })
        );
    }

    /**
     * Cria o usuário dono (TENANT_OWNER) no schema do Tenant.
     */
    public UserSummaryData createTenantOwner(
            String tenantSchema,
            Long accountId,
            String ownerDisplayName,
            String email,
            String rawPassword
    ) {
        tenantExecutor.assertTenantSchemaReadyOrThrow(tenantSchema, REQUIRED_TABLE);

        return tenantExecutor.runInTenantSchema(tenantSchema, () ->
                transactionExecutor.inTenantTx(() -> {

                    if (accountId == null) {
                        throw new ApiException("ACCOUNT_REQUIRED", "AccountId obrigatório", 400);
                    }

                    String emailNorm = EmailNormalizer.normalizeOrNull(email);
                    if (!StringUtils.hasText(emailNorm)) {
                        throw new ApiException("INVALID_EMAIL", "Email é obrigatório", 400);
                    }

                    if (!StringUtils.hasText(rawPassword)) {
                        throw new ApiException("INVALID_PASSWORD", "Senha é obrigatória", 400);
                    }

                    boolean emailExists = tenantUserRepository.existsByEmailAndAccountId(emailNorm, accountId);
                    if (emailExists) {
                        throw new ApiException("EMAIL_ALREADY_EXISTS", "Email já cadastrado nesta conta", 409);
                    }

                    String name = StringUtils.hasText(ownerDisplayName)
                            ? ownerDisplayName.trim()
                            : OWNER_NAME_FALLBACK;

                    Instant now = appNow();


                    TenantUser tenantUser = new TenantUser();
                    tenantUser.setAccountId(accountId);

                    // ✅ padronização: rename() (trim interno)
                    tenantUser.rename(name);

                    // ✅ padronização: changeEmail() (normaliza via EmailNormalizer)
                    tenantUser.changeEmail(emailNorm);

                    tenantUser.setPassword(passwordEncoder.encode(rawPassword));
                    tenantUser.setRole(TenantRole.TENANT_OWNER);

                    tenantUser.setSuspendedByAccount(false);
                    tenantUser.setSuspendedByAdmin(false);

                    tenantUser.setTimezone("America/Sao_Paulo");
                    tenantUser.setLocale("pt_BR");

                    tenantUser.setMustChangePassword(false);
                    tenantUser.setPasswordChangedAt(now);

                    TenantUser saved = tenantUserRepository.save(tenantUser);

                    return new UserSummaryData(
                            saved.getId(),
                            saved.getAccountId(),
                            saved.getName(),
                            saved.getEmail(),
                            saved.getRole() == null ? null : TenantRoleName.valueOf(saved.getRole().name()),
                            saved.isSuspendedByAccount(),
                            saved.isSuspendedByAdmin(),
                            saved.isDeleted()
                    );
                })
        );
    }

    /**
     * ✅ (SAFE) Admin bulk: suspende todos MENOS TENANT_OWNER.
     */
    public int suspendAllUsersByAccount(String tenantSchema, Long accountId) {
        tenantExecutor.assertTenantSchemaReadyOrThrow(tenantSchema, REQUIRED_TABLE);

        return tenantExecutor.runInTenantSchema(tenantSchema, () ->
                transactionExecutor.inTenantRequiresNew(() -> {

                    if (accountId == null) {
                        throw new ApiException("ACCOUNT_REQUIRED", "AccountId obrigatório", 400);
                    }

                    long ownersNotDeleted = tenantUserRepository.countNotDeletedByAccountIdAndRole(accountId, TenantRole.TENANT_OWNER);
                    if (ownersNotDeleted <= 0) {
                        throw new ApiException(
                                TENANT_OWNER_REQUIRED,
                                "Não é possível suspender usuários: não existe TENANT_OWNER não deletado para esta conta (estado inválido).",
                                409
                        );
                    }

                    return tenantUserRepository.suspendAllByAccountExceptRole(accountId, TenantRole.TENANT_OWNER);
                })
        );
    }

    /**
     * ✅ Reativa todos (inclusive owners).
     */
    public int unsuspendAllUsersByAccount(String tenantSchema, Long accountId) {
        return tenantExecutor.runInTenantSchemaIfReady(
                tenantSchema,
                REQUIRED_TABLE,
                () -> transactionExecutor.inTenantRequiresNew(() -> tenantUserRepository.unsuspendAllByAccount(accountId)),
                0
        );
    }

    /**
     * ✅ (SAFE) Cancelamento / exclusão da conta:
     * soft-delete de todos os usuários MENOS TENANT_OWNER.
     */
    public int softDeleteAllUsersByAccount(String tenantSchema, Long accountId) {
        return tenantExecutor.runInTenantSchemaIfReady(
                tenantSchema,
                REQUIRED_TABLE,
                () -> transactionExecutor.inTenantRequiresNew(() -> {

                    if (accountId == null) {
                        throw new ApiException("ACCOUNT_REQUIRED", "AccountId obrigatório", 400);
                    }

                    long ownersNotDeleted = tenantUserRepository.countNotDeletedByAccountIdAndRole(accountId, TenantRole.TENANT_OWNER);
                    if (ownersNotDeleted <= 0) {
                        throw new ApiException(
                                TENANT_OWNER_REQUIRED,
                                "Não é possível remover usuários: não existe TENANT_OWNER não deletado para esta conta (estado inválido).",
                                409
                        );
                    }

                    Instant now = appClock.instant();
                    return tenantUserRepository.softDeleteAllByAccountExceptRole(accountId, TenantRole.TENANT_OWNER, now);
                }),
                0
        );
    }

    public int restoreAllUsersByAccount(String tenantSchema, Long accountId) {
        return tenantExecutor.runInTenantSchemaIfReady(
                tenantSchema,
                REQUIRED_TABLE,
                () -> transactionExecutor.inTenantRequiresNew(() -> tenantUserRepository.restoreAllByAccount(accountId)),
                0
        );
    }

    public void setSuspendedByAdmin(String tenantSchema, Long accountId, Long userId, boolean suspended) {
        tenantExecutor.assertTenantSchemaReadyOrThrow(tenantSchema, REQUIRED_TABLE);

        tenantExecutor.runInTenantSchema(tenantSchema, () ->
                transactionExecutor.inTenantTx(() -> {

                    if (accountId == null) {
                        throw new ApiException("ACCOUNT_REQUIRED", "AccountId obrigatório", 400);
                    }
                    if (userId == null) {
                        throw new ApiException("USER_ID_REQUIRED", "userId obrigatório", 400);
                    }

                    if (suspended) {
                        TenantUser user = tenantUserRepository.findByIdAndAccountIdAndDeletedFalse(userId, accountId)
                                .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado ou removido", 404));

                        if (isActiveOwner(user)) {
                            long activeOwners = tenantUserRepository.countActiveOwnersByAccountId(accountId, TenantRole.TENANT_OWNER);
                            if (activeOwners <= 1) {
                                throw new ApiException(
                                        TENANT_OWNER_REQUIRED,
                                        "Não é permitido suspender o último TENANT_OWNER ativo.",
                                        409
                                );
                            }
                        }
                    }

                    int updated = tenantUserRepository.setSuspendedByAdmin(accountId, userId, suspended);
                    if (updated == 0) {
                        throw new ApiException("USER_NOT_FOUND", "Usuário não encontrado ou removido", 404);
                    }
                    return null;
                })
        );
    }

    public void setPasswordResetToken(String tenantSchema, Long accountId, Long userId, String token, Instant expiresAt) {
        tenantExecutor.runInTenantSchemaIfReady(tenantSchema, REQUIRED_TABLE, () ->
                transactionExecutor.inTenantTx(() -> {
                    TenantUser user = tenantUserRepository.findEnabledByIdAndAccountId(userId, accountId)
                            .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado", 404));

                    user.setPasswordResetToken(token);
                    user.setPasswordResetExpiresAt(expiresAt);

                    tenantUserRepository.save(user);
                    return null;
                })
        );
    }

    public TenantUser findByPasswordResetToken(String tenantSchema, Long accountId, String token) {
        tenantExecutor.assertTenantSchemaReadyOrThrow(tenantSchema, REQUIRED_TABLE);

        return tenantExecutor.runInTenantSchema(tenantSchema, () ->
                transactionExecutor.inTenantReadOnlyTx(() ->
                        tenantUserRepository.findByPasswordResetTokenAndAccountId(token, accountId)
                                .orElseThrow(() -> new ApiException("TOKEN_INVALID", "Token inválido", 400))
                )
        );
    }

    // =========================================================
    // helpers
    // =========================================================

    private boolean isActiveOwner(TenantUser user) {
        if (user == null) return false;
        if (user.isDeleted()) return false;
        if (user.isSuspendedByAccount()) return false;
        if (user.isSuspendedByAdmin()) return false;
        return user.getRole() != null && user.getRole().isTenantOwner();
    }
    
    private Instant appNow() {
        return appClock.instant();
    }

}
