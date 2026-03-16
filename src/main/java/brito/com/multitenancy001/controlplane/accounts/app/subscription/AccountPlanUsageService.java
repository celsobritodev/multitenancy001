package brito.com.multitenancy001.controlplane.accounts.app.subscription;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.infrastructure.tenant.TenantExecutor;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.products.persistence.TenantProductRepository;
import brito.com.multitenancy001.tenant.users.persistence.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Serviço de aplicação responsável por calcular o uso atual da conta
 * para fins de preview/elegibilidade de mudança de plano.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Carregar a conta no public schema</li>
 *   <li>Contar usuários habilitados no tenant schema</li>
 *   <li>Contar produtos não deletados no tenant schema</li>
 *   <li>Resolver storage atual</li>
 * </ul>
 *
 * <p>Regras:</p>
 * <ul>
 *   <li>Conta built-in retorna uso zerado</li>
 *   <li>Leitura da conta ocorre no public schema</li>
 *   <li>Leituras de usuários/produtos ocorrem dentro do tenant schema</li>
 *   <li>Conta tenant sem tenantSchema válido falha explicitamente</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountPlanUsageService {

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final AccountRepository accountRepository;
    private final TenantExecutor tenantExecutor;
    private final TenantUserRepository tenantUserRepository;
    private final TenantProductRepository tenantProductRepository;
    private final AccountStorageUsageResolver accountStorageUsageResolver;

    /**
     * Calcula o snapshot completo de uso por id da conta.
     *
     * @param accountId id da conta
     * @return snapshot de uso
     */
    public PlanUsageSnapshot calculateUsageByAccountId(Long accountId) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        }

        Account account = publicSchemaUnitOfWork.readOnly(() ->
                accountRepository.findByIdAndDeletedFalse(accountId)
                        .orElseThrow(() -> new ApiException(
                                ApiErrorCode.ACCOUNT_NOT_FOUND,
                                "Conta não encontrada com id: " + accountId,
                                404
                        ))
        );

        return calculateUsage(account);
    }

    /**
     * Calcula o snapshot completo de uso a partir da própria conta.
     *
     * @param account conta já resolvida
     * @return snapshot de uso
     */
    public PlanUsageSnapshot calculateUsage(Account account) {
        validateAccount(account);

        if (account.isBuiltInAccount()) {
            log.info(
                    "Uso de plano calculado para conta built-in. accountId={}, plan={}",
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

        long currentUsers = tenantExecutor.runInTenantSchema(
                tenantSchema,
                () -> tenantUserRepository.countEnabledUsersByAccount(account.getId())
        );

        long currentProducts = tenantExecutor.runInTenantSchema(
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
     * Recarrega a conta no public schema e calcula uso somente se a conta for operacional.
     *
     * <p>Útil para cenários futuros em que a operação exija conta habilitada.</p>
     *
     * @param accountId id da conta
     * @return snapshot de uso
     */
    public PlanUsageSnapshot calculateUsageForEnabledAccount(Long accountId) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        }

        Account account = publicSchemaUnitOfWork.readOnly(() ->
                accountRepository.findEnabledById(accountId)
                        .orElseThrow(() -> new ApiException(
                                ApiErrorCode.ACCOUNT_NOT_FOUND,
                                "Conta operacional não encontrada com id: " + accountId,
                                404
                        ))
        );

        return calculateUsage(account);
    }

    /**
     * Valida a conta recebida.
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
    }

    /**
     * Normaliza e valida tenant schema.
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