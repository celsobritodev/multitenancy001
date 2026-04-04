package brito.com.multitenancy001.tenant.provisioning.app;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.infrastructure.persistence.PublicTxExecutor;
import brito.com.multitenancy001.infrastructure.tenant.TenantContextExecutor;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.contracts.UserSummaryData;
import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import brito.com.multitenancy001.shared.domain.service.LoginIdentityService;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.executor.TenantToPublicBridgeExecutor;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.security.TenantRoleName;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.security.TenantRole;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import brito.com.multitenancy001.tenant.users.persistence.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Application Service de provisioning de usuários no schema do tenant.
 *
 * <p><b>Responsabilidades:</b></p>
 * <ul>
 *   <li>Criar e consultar usuários operacionais dentro do schema TENANT.</li>
 *   <li>Executar operações administrativas em massa sobre usuários do tenant.</li>
 *   <li>Provisionar, quando necessário, a identidade de login no PUBLIC de forma
 *       síncrona e controlada.</li>
 * </ul>
 *
 * <p><b>Regra arquitetural crítica:</b></p>
 * <ul>
 *   <li>Operações TENANT devem permanecer dentro do {@link TenantContextExecutor} +
 *       {@link PublicTxExecutor} do tenant.</li>
 *   <li>Operações PUBLIC subsequentes devem ocorrer via
 *       {@link TenantToPublicBridgeExecutor} antes do
 *       {@link PublicSchemaUnitOfWork}.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantUserProvisioningService {

    private static final String REQUIRED_TABLE = "tenant_users";
    private static final String OWNER_NAME_FALLBACK = "Owner";

    private final TenantContextExecutor tenantExecutor;
    private final PublicTxExecutor transactionExecutor;

    private final TenantUserRepository tenantUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppClock appClock;

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final TenantToPublicBridgeExecutor tenantToPublicBridgeExecutor;
    private final LoginIdentityService loginIdentityService;

    /**
     * Lista resumos de usuários do tenant.
     *
     * @param tenantSchema schema do tenant
     * @param accountId id da conta
     * @param onlyOperational quando {@code true}, retorna apenas usuários operacionais ativos
     * @return lista resumida de usuários
     */
    public List<UserSummaryData> listUserSummaries(String tenantSchema, Long accountId, boolean onlyOperational) {
        log.debug("listUserSummaries chamado | tenantSchema={} accountId={} onlyOperational={}",
                tenantSchema, accountId, onlyOperational);

        tenantExecutor.assertTenantSchemaReadyOrThrow(tenantSchema, REQUIRED_TABLE);

        List<UserSummaryData> result = tenantExecutor.runInTenantSchema(tenantSchema, () ->
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

        log.debug("listUserSummaries concluído | tenantSchema={} accountId={} total={}",
                tenantSchema, accountId, result != null ? result.size() : 0);

        return result;
    }

    /**
     * Cria o usuário dono ({@code TENANT_OWNER}) no schema do tenant e garante,
     * de forma síncrona, a identidade de login no PUBLIC.
     *
     * <p><b>Fluxo:</b></p>
     * <ol>
     *   <li>Cria o usuário no schema TENANT em transação tenant.</li>
     *   <li>Após o sucesso do create, executa provisioning do índice de login no PUBLIC.</li>
     *   <li>O passo PUBLIC ocorre via bridge explícito TENANT -&gt; PUBLIC.</li>
     * </ol>
     *
     * @param tenantSchema schema do tenant
     * @param accountId id da conta
     * @param ownerDisplayName nome do owner
     * @param email email do owner
     * @param rawPassword senha bruta
     * @return resumo do usuário criado
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

        // =========================================================
        // FASE 1 — TENANT
        // =========================================================
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
                        log.warn("Email já existe no tenant | email={} accountId={}", emailNorm, accountId);
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

                    log.info("✅ Tenant owner salvo no tenant | tenantSchema={} accountId={} userId={} email={}",
                            tenantSchema, accountId, saved.getId(), saved.getEmail());

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

        // =========================================================
        // FASE 2 — PUBLIC
        // =========================================================
        log.info("Garantindo identidade de login no PUBLIC | accountId={} email={}", accountId, created.email());

        try {
            tenantToPublicBridgeExecutor.run(() ->
                    publicSchemaUnitOfWork.requiresNew(() -> {
                        loginIdentityService.ensureTenantIdentity(created.email(), accountId);
                        return null;
                    })
            );

            log.info("✅ Identidade de login garantida no PUBLIC | accountId={} email={}", accountId, created.email());

        } catch (Exception e) {
            log.error("❌ Falha ao garantir identidade de login no PUBLIC | accountId={} email={}",
                    accountId, created.email(), e);

            throw new ApiException(
                    ApiErrorCode.INTERNAL_ERROR,
                    "Falha ao provisionar identidade de login para o tenant owner",
                    500
            );
        }

        log.info("✅ createTenantOwner CONCLUÍDO COM SUCESSO | tenantSchema={} accountId={} userId={} email={}",
                tenantSchema, accountId, created.id(), created.email());

        return created;
    }

    /**
     * Suspende todos os usuários da conta, exceto o {@code TENANT_OWNER}.
     *
     * @param tenantSchema schema do tenant
     * @param accountId id da conta
     * @return quantidade de usuários afetados
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

        log.info("✅ suspendAllUsersByAccount CONCLUÍDO | tenantSchema={} accountId={} usuáriosAfetados={}",
                tenantSchema, accountId, result);

        return result;
    }

    /**
     * Reativa todos os usuários da conta.
     *
     * @param tenantSchema schema do tenant
     * @param accountId id da conta
     * @return quantidade de usuários afetados
     */
    public int unsuspendAllUsersByAccount(String tenantSchema, Long accountId) {
        log.info("unsuspendAllUsersByAccount INICIANDO | tenantSchema={} accountId={}", tenantSchema, accountId);

        int result = tenantExecutor.runInTenantSchemaIfReady(
                tenantSchema,
                REQUIRED_TABLE,
                () -> transactionExecutor.inTenantRequiresNew(() -> {
                    int updated = tenantUserRepository.unsuspendAllByAccount(accountId);
                    log.info("✅ unsuspendAllUsersByAccount executado | tenantSchema={} accountId={} usuáriosReativados={}",
                            tenantSchema, accountId, updated);
                    return updated;
                }),
                0
        );

        log.info("✅ unsuspendAllUsersByAccount CONCLUÍDO | tenantSchema={} accountId={} usuáriosAfetados={}",
                tenantSchema, accountId, result);

        return result;
    }

    /**
     * Realiza soft delete de todos os usuários da conta, exceto o {@code TENANT_OWNER}.
     *
     * @param tenantSchema schema do tenant
     * @param accountId id da conta
     * @return quantidade de usuários afetados
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

        log.info("✅ softDeleteAllUsersByAccount CONCLUÍDO | tenantSchema={} accountId={} usuáriosAfetados={}",
                tenantSchema, accountId, result);

        return result;
    }

    /**
     * Restaura todos os usuários soft-deletados da conta.
     *
     * @param tenantSchema schema do tenant
     * @param accountId id da conta
     * @return quantidade de usuários afetados
     */
    public int restoreAllUsersByAccount(String tenantSchema, Long accountId) {
        log.info("restoreAllUsersByAccount INICIANDO | tenantSchema={} accountId={}", tenantSchema, accountId);

        int result = tenantExecutor.runInTenantSchemaIfReady(
                tenantSchema,
                REQUIRED_TABLE,
                () -> transactionExecutor.inTenantRequiresNew(() -> {
                    int updated = tenantUserRepository.restoreAllByAccount(accountId);
                    log.info("✅ restoreAllUsersByAccount executado | tenantSchema={} accountId={} usuáriosRestaurados={}",
                            tenantSchema, accountId, updated);
                    return updated;
                }),
                0
        );

        log.info("✅ restoreAllUsersByAccount CONCLUÍDO | tenantSchema={} accountId={} usuáriosAfetados={}",
                tenantSchema, accountId, result);

        return result;
    }

    // =========================================================
    // Helpers
    // =========================================================

    /**
     * Retorna o instante atual via {@link AppClock}.
     *
     * @return instante atual da aplicação
     */
    private Instant appNow() {
        return appClock.instant();
    }
}