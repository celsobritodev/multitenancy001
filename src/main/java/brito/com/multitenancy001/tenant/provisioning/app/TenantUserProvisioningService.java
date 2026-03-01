package brito.com.multitenancy001.tenant.provisioning.app;

import brito.com.multitenancy001.infrastructure.persistence.TxExecutor;
import brito.com.multitenancy001.infrastructure.tenant.TenantExecutor;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.contracts.UserSummaryData;
import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.persistence.publicschema.LoginIdentityProvisioningService;
import brito.com.multitenancy001.shared.security.TenantRoleName;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.security.TenantRole;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import brito.com.multitenancy001.tenant.users.persistence.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantUserProvisioningService {

    private static final String REQUIRED_TABLE = "tenant_users";
    private static final String OWNER_NAME_FALLBACK = "Owner";

    private final TenantExecutor tenantExecutor;
    private final TxExecutor transactionExecutor;

    private final TenantUserRepository tenantUserRepository;
    private final PasswordEncoder passwordEncoder;

    private final AppClock appClock;

    // ✅ NOVO: PUBLIC UoW + provisioning do índice de login
    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final LoginIdentityProvisioningService loginIdentityProvisioningService;

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
     *
     * <p>Correção crítica (login multi-tenant):</p>
     * <ul>
     *   <li>Após criar o usuário no schema TENANT, deve provisionar o índice PUBLIC em {@code public.login_identities}.</li>
     *   <li>Isso é feito FORA da transação TENANT, via {@link PublicSchemaUnitOfWork#requiresNew(Runnable)}.</li>
     * </ul>
     */
    public UserSummaryData createTenantOwner(
            String tenantSchema,
            Long accountId,
            String ownerDisplayName,
            String email,
            String rawPassword
    ) {
        tenantExecutor.assertTenantSchemaReadyOrThrow(tenantSchema, REQUIRED_TABLE);

        // 1) Faz o create inteiro no TENANT (TX TENANT)
        UserSummaryData created = tenantExecutor.runInTenantSchema(tenantSchema, () ->
                transactionExecutor.inTenantTx(() -> {

                    if (accountId == null) {
                        throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "AccountId obrigatório", 400);
                    }

                    String emailNorm = EmailNormalizer.normalizeOrNull(email);
                    if (!StringUtils.hasText(emailNorm)) {
                        throw new ApiException(ApiErrorCode.INVALID_EMAIL, "Email é obrigatório", 400);
                    }

                    if (!StringUtils.hasText(rawPassword)) {
                        throw new ApiException(ApiErrorCode.INVALID_PASSWORD, "Senha é obrigatória", 400);
                    }

                    boolean emailExists = tenantUserRepository.existsByEmailAndAccountId(emailNorm, accountId);
                    if (emailExists) {
                    	throw new ApiException(
                    		    ApiErrorCode.EMAIL_ALREADY_REGISTERED_IN_ACCOUNT,
                    		    null,
                    		    Map.of("accountId", accountId, "email", email)
                    		);
                    }

                    String name = StringUtils.hasText(ownerDisplayName)
                            ? ownerDisplayName.trim()
                            : OWNER_NAME_FALLBACK;

                    Instant now = appNow();

                    TenantUser tenantUser = new TenantUser();
                    tenantUser.setAccountId(accountId);

                    tenantUser.rename(name);
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

        // 2) Fora da TX TENANT: provisiona o índice PUBLIC (REQUIRES_NEW)
        publicSchemaUnitOfWork.requiresNew(() ->
                loginIdentityProvisioningService.ensureTenantIdentityAfterCompletion(created.email(), accountId)
        );

        return created;
    }

    /**
     * ✅ (SAFE) Admin bulk: suspende todos MENOS TENANT_OWNER.
     */
    public int suspendAllUsersByAccount(String tenantSchema, Long accountId) {
        tenantExecutor.assertTenantSchemaReadyOrThrow(tenantSchema, REQUIRED_TABLE);

        return tenantExecutor.runInTenantSchema(tenantSchema, () ->
                transactionExecutor.inTenantRequiresNew(() -> {

                    if (accountId == null) {
                        throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "AccountId obrigatório", 400);
                    }

                    long ownersNotDeleted = tenantUserRepository.countNotDeletedByAccountIdAndRole(accountId, TenantRole.TENANT_OWNER);
                    if (ownersNotDeleted <= 0) {
                        throw new ApiException(
                                ApiErrorCode.TENANT_OWNER_REQUIRED,
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
                        throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "AccountId obrigatório", 400);
                    }

                    long ownersNotDeleted = tenantUserRepository.countNotDeletedByAccountIdAndRole(accountId, TenantRole.TENANT_OWNER);
                    if (ownersNotDeleted <= 0) {
                        throw new ApiException(
                                ApiErrorCode.TENANT_OWNER_REQUIRED,
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

    // =========================================================
    // helpers
    // =========================================================

    private Instant appNow() {
        return appClock.instant();
    }
}