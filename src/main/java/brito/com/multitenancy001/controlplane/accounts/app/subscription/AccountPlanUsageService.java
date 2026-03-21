package brito.com.multitenancy001.controlplane.accounts.app.subscription;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.infrastructure.tenant.TenantSchemaUnitOfWork;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.products.persistence.TenantProductRepository;
import brito.com.multitenancy001.tenant.users.persistence.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de aplicação responsável por calcular o uso atual da conta
 * para fins de preview e elegibilidade de mudança de plano.
 *
 * <p><b>Responsabilidades:</b></p>
 * <ul>
 *   <li>Carregar a conta no Public Schema quando necessário.</li>
 *   <li>Contar usuários habilitados no Tenant Schema.</li>
 *   <li>Contar produtos não deletados no Tenant Schema.</li>
 *   <li>Resolver storage atual consumido pela conta.</li>
 *   <li>Manter coerência entre snapshot de uso e enforcement de quotas.</li>
 * </ul>
 *
 * <p><b>Regras arquiteturais:</b></p>
 * <ul>
 *   <li>{@link #calculateUsageByAccountId(Long)} e
 *       {@link #calculateUsageForEnabledAccount(Long)} iniciam pelo PUBLIC.</li>
 *   <li>{@link #calculateUsage(Account)} assume que a conta já foi resolvida
 *       e segue para leituras tenant-aware apenas fora de TX pública.</li>
 *   <li>Conta built-in retorna uso zerado.</li>
 *   <li>Conta sem tenantSchema válido falha explicitamente.</li>
 *   <li>A semântica de contagem deve permanecer alinhada com {@code TenantQuotaEnforcementService}.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountPlanUsageService {

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final AccountRepository accountRepository;
    private final TenantSchemaUnitOfWork tenantSchemaUnitOfWork;
    private final TenantUserRepository tenantUserRepository;
    private final TenantProductRepository tenantProductRepository;
    private final AccountStorageUsageResolver accountStorageUsageResolver;

    /**
     * Calcula o snapshot completo de uso a partir do id da conta.
     *
     * <p>Este método inicia em contexto PUBLIC para recarregar a conta e,
     * depois, delega ao cálculo tenant-aware.</p>
     *
     * @param accountId id da conta
     * @return snapshot completo de uso
     */
    public PlanUsageSnapshot calculateUsageByAccountId(Long accountId) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        }

        log.info("Calculando uso de plano por accountId. accountId={}", accountId);

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
                "Uso de plano calculado por accountId com sucesso. accountId={}, plan={}, users={}, products={}, storageMb={}",
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
     * <p>Este método não recarrega a conta no PUBLIC. Ele parte do princípio
     * de que a conta já foi obtida corretamente em etapa anterior.</p>
     *
     * @param account conta já resolvida
     * @return snapshot completo de uso
     */
    public PlanUsageSnapshot calculateUsage(Account account) {
        validateAccount(account);

        log.info(
                "Calculando uso de plano a partir da conta já resolvida. accountId={}, plan={}, tenantSchema={}, builtIn={}",
                account.getId(),
                account.getSubscriptionPlan(),
                account.getTenantSchema(),
                account.isBuiltInAccount()
        );

        if (account.isBuiltInAccount()) {
            log.info(
                    "Conta built-in detectada durante cálculo de uso. accountId={}, plan={}",
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

        log.info(
                "Iniciando leituras tenant-aware para cálculo de uso. accountId={}, tenantSchema={}",
                account.getId(),
                tenantSchema
        );

        long currentUsers = tenantSchemaUnitOfWork.readOnly(
                tenantSchema,
                () -> tenantUserRepository.countEnabledUsersByAccount(account.getId())
        );

        long currentProducts = tenantSchemaUnitOfWork.readOnly(
                tenantSchema,
                tenantProductRepository::countByDeletedFalse
        );

        long currentStorageMb = accountStorageUsageResolver.resolveStorageMb(account.getId());

        PlanUsageSnapshot snapshot = new PlanUsageSnapshot(
                account.getId(),
                account.getSubscriptionPlan(),
                currentUsers,
                currentProducts,
                currentStorageMb
        );

        log.info(
                "Uso de plano calculado com sucesso. accountId={}, tenantSchema={}, plan={}, users={}, products={}, storageMb={}",
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
     * <p>Útil para cenários em que a operação exige conta habilitada
     * antes de prosseguir para o cálculo tenant-aware.</p>
     *
     * @param accountId id da conta
     * @return snapshot de uso da conta operacional
     */
    public PlanUsageSnapshot calculateUsageForEnabledAccount(Long accountId) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        }

        log.info("Calculando uso de plano para conta operacional. accountId={}", accountId);

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
                "Uso de plano para conta operacional calculado com sucesso. accountId={}, plan={}, users={}, products={}, storageMb={}",
                snapshot.accountId(),
                snapshot.currentPlan(),
                snapshot.currentUsers(),
                snapshot.currentProducts(),
                snapshot.currentStorageMb()
        );

        return snapshot;
    }

    /**
     * Valida a conta recebida para cálculo de uso.
     *
     * @param account conta a validar
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
    }

    /**
     * Normaliza e valida o tenant schema da conta.
     *
     * @param tenantSchema schema bruto
     * @return schema normalizado
     */
    private String normalizeTenantSchema(String tenantSchema) {
        if (!StringUtils.hasText(tenantSchema)) {
            throw new ApiException(ApiErrorCode.ACCOUNT_NOT_READY, "Conta sem tenant schema", 409);
        }

        return tenantSchema.trim();
    }
}