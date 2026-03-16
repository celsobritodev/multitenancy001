package brito.com.multitenancy001.controlplane.accounts.app.subscription;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.AccountEntitlements;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountEntitlementsRepository;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Serviço responsável por sincronizar o snapshot materializado de entitlements
 * com base no plano vigente da conta.
 *
 * <p>Regras:</p>
 * <ul>
 *   <li>Executa no public schema</li>
 *   <li>Não decide elegibilidade comercial</li>
 *   <li>Não faz billing</li>
 *   <li>Apenas materializa os limites efetivos derivados do plano vigente</li>
 * </ul>
 *
 * <p>Estratégia de persistência:</p>
 * <ul>
 *   <li>Usa upsert semântico no repository</li>
 *   <li>Insere se não existir</li>
 *   <li>Atualiza se já existir</li>
 *   <li>Relê o snapshot ao final para devolver o estado persistido</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountEntitlementsSynchronizationService {

    private final SubscriptionPlanCatalog subscriptionPlanCatalog;
    private final AccountEntitlementsRepository accountEntitlementsRepository;
    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final AppClock appClock;

    /**
     * Sincroniza os entitlements persistidos com o plano atual da conta.
     *
     * @param account conta alvo
     * @return entitlements atualizados
     */
    public AccountEntitlements synchronizeToCurrentPlan(Account account) {
        validateAccount(account);

        if (account.isBuiltInAccount()) {
            log.info("Sincronização de entitlements ignorada para conta built-in. accountId={}", account.getId());
            return null;
        }

        PlanLimitSnapshot limits = subscriptionPlanCatalog.resolveLimits(account.getSubscriptionPlan());

        return publicSchemaUnitOfWork.tx(() -> {
            int affectedRows = accountEntitlementsRepository.upsertSnapshot(
                    account.getId(),
                    limits.maxUsers(),
                    limits.maxProducts(),
                    limits.maxStorageMb(),
                    appClock.instant()
            );

            AccountEntitlements saved = accountEntitlementsRepository.findByAccount_Id(account.getId())
                    .orElseThrow(() -> new ApiException(
                            ApiErrorCode.INVALID_ENTITLEMENT,
                            "Falha ao reler account_entitlements após sincronização da conta " + account.getId(),
                            500
                    ));

            log.info(
                    "Entitlements sincronizados com sucesso. accountId={}, plan={}, maxUsers={}, maxProducts={}, maxStorageMb={}, affectedRows={}",
                    account.getId(),
                    account.getSubscriptionPlan(),
                    saved.getMaxUsers(),
                    saved.getMaxProducts(),
                    saved.getMaxStorageMb(),
                    affectedRows
            );

            return saved;
        });
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
    }
}