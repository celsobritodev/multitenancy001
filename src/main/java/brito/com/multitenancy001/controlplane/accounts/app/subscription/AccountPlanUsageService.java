package brito.com.multitenancy001.controlplane.accounts.app.subscription;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.integration.tenant.dto.TenantUsageSnapshot;
import brito.com.multitenancy001.integration.tenant.subscription.TenantSubscriptionUsageIntegrationService;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de aplicação responsável por calcular o uso atual da conta
 * para fins de preview e elegibilidade de mudança de plano.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Carregar a conta no Public Schema quando necessário.</li>
 *   <li>Delegar a medição tenant-aware para a camada explícita de integração.</li>
 *   <li>Resolver o storage atual consumido pela conta.</li>
 *   <li>Montar o snapshot consolidado de uso do plano.</li>
 * </ul>
 *
 * <p>Regras arquiteturais:</p>
 * <ul>
 *   <li>O crossing ControlPlane → Tenant é feito somente por
 *       {@link TenantSubscriptionUsageIntegrationService}.</li>
 *   <li>Conta built-in retorna uso zerado.</li>
 *   <li>Conta sem tenantSchema válido falha explicitamente.</li>
 *   <li>Este service não usa repository do contexto Tenant.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountPlanUsageService {

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final AccountRepository accountRepository;
    private final TenantSubscriptionUsageIntegrationService tenantSubscriptionUsageIntegrationService;
    private final AccountStorageUsageResolver accountStorageUsageResolver;

    /**
     * Calcula o snapshot completo de uso a partir do id da conta.
     *
     * @param accountId id da conta
     * @return snapshot completo de uso
     */
    public PlanUsageSnapshot calculateUsageByAccountId(Long accountId) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        }

        log.info("Calculando uso do plano por accountId. accountId={}", accountId);

        Account account = publicSchemaUnitOfWork.readOnly(() ->
                accountRepository.findByIdAndDeletedFalse(accountId)
                        .orElseThrow(() -> new ApiException(
                                ApiErrorCode.ACCOUNT_NOT_FOUND,
                                "Conta não encontrada com id: " + accountId,
                                404
                        ))
        );

        PlanUsageSnapshot snapshot = calculateUsage(account);

        log.info(
                "Uso do plano calculado por accountId com sucesso. accountId={}, currentPlan={}, users={}, products={}, storageMb={}",
                snapshot.accountId(),
                snapshot.currentPlan(),
                snapshot.currentUsers(),
                snapshot.currentProducts(),
                snapshot.currentStorageMb()
        );

        return snapshot;
    }

    /**
     * Calcula o snapshot completo de uso a partir da própria conta.
     *
     * @param account conta já resolvida
     * @return snapshot completo de uso
     */
    public PlanUsageSnapshot calculateUsage(Account account) {
        validateAccount(account);

        log.info(
                "Calculando uso do plano a partir da conta já resolvida. accountId={}, currentPlan={}, tenantSchema={}, builtIn={}",
                account.getId(),
                account.getSubscriptionPlan(),
                account.getTenantSchema(),
                account.isBuiltInAccount()
        );

        if (account.isBuiltInAccount()) {
            log.info(
                    "Conta built-in detectada durante cálculo de uso. accountId={}, currentPlan={}",
                    account.getId(),
                    account.getSubscriptionPlan()
            );

            return new PlanUsageSnapshot(
                    account.getId(),
                    account.getSubscriptionPlan(),
                    0L,
                    0L,
                    0L
            );
        }

        String tenantSchema = normalizeTenantSchema(account.getTenantSchema());

        TenantUsageSnapshot tenantUsageSnapshot = tenantSubscriptionUsageIntegrationService.measureUsage(
                tenantSchema,
                account.getId()
        );

        long currentStorageMb = accountStorageUsageResolver.resolveStorageMb(account.getId());

        PlanUsageSnapshot snapshot = new PlanUsageSnapshot(
                account.getId(),
                account.getSubscriptionPlan(),
                tenantUsageSnapshot.currentUsers(),
                tenantUsageSnapshot.currentProducts(),
                currentStorageMb
        );

        log.info(
                "Uso do plano calculado com sucesso. accountId={}, tenantSchema={}, currentPlan={}, users={}, products={}, storageMb={}",
                snapshot.accountId(),
                tenantSchema,
                snapshot.currentPlan(),
                snapshot.currentUsers(),
                snapshot.currentProducts(),
                snapshot.currentStorageMb()
        );

        return snapshot;
    }

    /**
     * Recarrega a conta operacional no Public Schema e calcula seu uso atual.
     *
     * @param accountId id da conta
     * @return snapshot de uso da conta operacional
     */
    public PlanUsageSnapshot calculateUsageForEnabledAccount(Long accountId) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        }

        log.info("Calculando uso do plano para conta operacional. accountId={}", accountId);

        Account account = publicSchemaUnitOfWork.readOnly(() ->
                accountRepository.findEnabledById(accountId)
                        .orElseThrow(() -> new ApiException(
                                ApiErrorCode.ACCOUNT_NOT_FOUND,
                                "Conta operacional não encontrada com id: " + accountId,
                                404
                        ))
        );

        PlanUsageSnapshot snapshot = calculateUsage(account);

        log.info(
                "Uso do plano para conta operacional calculado com sucesso. accountId={}, currentPlan={}, users={}, products={}, storageMb={}",
                snapshot.accountId(),
                snapshot.currentPlan(),
                snapshot.currentUsers(),
                snapshot.currentProducts(),
                snapshot.currentStorageMb()
        );

        return snapshot;
    }

    /**
     * Valida a conta de entrada.
     *
     * @param account conta
     */
    private void validateAccount(Account account) {
        if (account == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "Conta é obrigatória", 400);
        }

        if (account.getId() == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        }

        if (account.getSubscriptionPlan() == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "subscriptionPlan é obrigatório", 400);
        }

        if (!account.isBuiltInAccount() && !StringUtils.hasText(account.getTenantSchema())) {
            throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatório", 400);
        }
    }

    /**
     * Normaliza o schema do tenant.
     *
     * @param tenantSchema schema bruto
     * @return schema normalizado
     */
    private String normalizeTenantSchema(String tenantSchema) {
        if (!StringUtils.hasText(tenantSchema)) {
            throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatório", 400);
        }

        String normalizedTenantSchema = tenantSchema.trim();

        if (!StringUtils.hasText(normalizedTenantSchema)) {
            throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatório", 400);
        }

        return normalizedTenantSchema;
    }
}