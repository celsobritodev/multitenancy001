package brito.com.multitenancy001.tenant.provisioning.app;

import brito.com.multitenancy001.infrastructure.persistence.TxExecutor;
import brito.com.multitenancy001.infrastructure.tenant.TenantExecutor;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.contracts.UserSummaryData;
import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.domain.service.LoginIdentityService; // NOVA INTERFACE
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

    // ✅ NOVO: PUBLIC UoW + provisioning do índice de login usando a interface
    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final LoginIdentityService loginIdentityService;

    public List<UserSummaryData> listUserSummaries(String tenantSchema, Long accountId, boolean onlyOperational) {
        log.debug("listUserSummaries chamado | tenantSchema={} accountId={} onlyOperational={}", 
                tenantSchema, accountId, onlyOperational);
        
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
     *   <li>Após criar o usuário no schema TENANT, provisiona o índice PUBLIC de forma SÍNCRONA.</li>
     *   <li>Se falhar, a transação do TENANT é revertida.</li>
     * </ul>
     */
    public UserSummaryData createTenantOwner(
            String tenantSchema,
            Long accountId,
            String ownerDisplayName,
            String email,
            String rawPassword
    ) {
        log.info("🚀 createTenantOwner INICIANDO | tenantSchema={} accountId={} email={}", 
                tenantSchema, accountId, email);
        
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
                        log.warn("Email já existe | email={} accountId={}", emailNorm, accountId);
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
                    log.info("✅ Tenant owner salvo no tenant | userId={} email={}", saved.getId(), saved.getEmail());

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

        // 2) Provisiona o índice PUBLIC de forma SÍNCRONA (REQUIRES_NEW)
        log.info("Garantindo identidade de login no PUBLIC | email={} accountId={}", created.email(), accountId);
        try {
            publicSchemaUnitOfWork.requiresNew(() -> {
                loginIdentityService.ensureTenantIdentity(created.email(), accountId);
                return null;
            });
            log.info("✅ Identidade de login garantida no PUBLIC | email={} accountId={}", created.email(), accountId);
        } catch (Exception e) {
            log.error("❌ Falha ao garantir identidade de login | email={} accountId={}", created.email(), accountId, e);
            throw new ApiException(ApiErrorCode.INTERNAL_ERROR, 
                "Falha ao provisionar identidade de login para o tenant owner", 500);
        }

        log.info("✅ createTenantOwner CONCLUÍDO COM SUCESSO | userId={} email={}", created.id(), created.email());
        return created;
    }

    /**
     * ✅ (SAFE) Admin bulk: suspende todos MENOS TENANT_OWNER.
     */
    public int suspendAllUsersByAccount(String tenantSchema, Long accountId) {
        log.info("suspendAllUsersByAccount INICIANDO | tenantSchema={} accountId={}", tenantSchema, accountId);
        
        tenantExecutor.assertTenantSchemaReadyOrThrow(tenantSchema, REQUIRED_TABLE);

        int result = tenantExecutor.runInTenantSchema(tenantSchema, () ->
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
        
        log.info("✅ suspendAllUsersByAccount CONCLUÍDO | tenantSchema={} accountId={} usuários afetados={}", 
                tenantSchema, accountId, result);
        return result;
    }

    /**
     * ✅ Reativa todos (inclusive owners).
     */
    public int unsuspendAllUsersByAccount(String tenantSchema, Long accountId) {
        log.info("unsuspendAllUsersByAccount INICIANDO | tenantSchema={} accountId={}", tenantSchema, accountId);
        
        int result = tenantExecutor.runInTenantSchemaIfReady(
                tenantSchema,
                REQUIRED_TABLE,
                () -> transactionExecutor.inTenantRequiresNew(() -> {
                    int updated = tenantUserRepository.unsuspendAllByAccount(accountId);
                    log.info("✅ unsuspendAllUsersByAccount executado | usuários reativados={}", updated);
                    return updated;
                }),
                0
        );
        
        log.info("✅ unsuspendAllUsersByAccount CONCLUÍDO | tenantSchema={} accountId={} usuários afetados={}", 
                tenantSchema, accountId, result);
        return result;
    }

    /**
     * ✅ (SAFE) Cancelamento / exclusão da conta:
     * soft-delete de todos os usuários MENOS TENANT_OWNER.
     */
    public int softDeleteAllUsersByAccount(String tenantSchema, Long accountId) {
        log.info("softDeleteAllUsersByAccount INICIANDO | tenantSchema={} accountId={}", tenantSchema, accountId);
        
        int result = tenantExecutor.runInTenantSchemaIfReady(
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
        
        log.info("✅ softDeleteAllUsersByAccount CONCLUÍDO | tenantSchema={} accountId={} usuários afetados={}", 
                tenantSchema, accountId, result);
        return result;
    }

    public int restoreAllUsersByAccount(String tenantSchema, Long accountId) {
        log.info("restoreAllUsersByAccount INICIANDO | tenantSchema={} accountId={}", tenantSchema, accountId);
        
        int result = tenantExecutor.runInTenantSchemaIfReady(
                tenantSchema,
                REQUIRED_TABLE,
                () -> transactionExecutor.inTenantRequiresNew(() -> {
                    int updated = tenantUserRepository.restoreAllByAccount(accountId);
                    log.info("✅ restoreAllUsersByAccount executado | usuários restaurados={}", updated);
                    return updated;
                }),
                0
        );
        
        log.info("✅ restoreAllUsersByAccount CONCLUÍDO | tenantSchema={} accountId={} usuários afetados={}", 
                tenantSchema, accountId, result);
        return result;
    }

    // =========================================================
    // helpers
    // =========================================================

    private Instant appNow() {
        return appClock.instant();
    }
}