package brito.com.multitenancy001.controlplane.accounts.app.subscription;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.AccountUsageSnapshot;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço responsável por calcular o snapshot de uso do plano
 * para fins de preview e elegibilidade de mudança de plano.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Resolver a conta no PUBLIC schema quando necessário.</li>
 *   <li>Traduzir o snapshot materializado público em {@link PlanUsageSnapshot}.</li>
 *   <li>Tratar contas built-in como uso zero, fora do fluxo comercial.</li>
 * </ul>
 *
 * <p>Regras arquiteturais desta versão:</p>
 * <ul>
 *   <li>O Control Plane não mede uso entrando no tenant.</li>
 *   <li>O cálculo decisório passa a ler somente snapshot materializado no schema public.</li>
 *   <li>Se o snapshot público ainda não existir para conta comercial, a operação falha explicitamente.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountPlanUsageService {

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final AccountRepository accountRepository;
    private final AccountUsageSnapshotQueryService accountUsageSnapshotQueryService;

    /**
     * Calcula o snapshot completo de uso a partir do id da conta.
     *
     * @param accountId id da conta
     * @return snapshot completo de uso
     */
    public PlanUsageSnapshot calculateUsageByAccountId(Long accountId) {
        SubscriptionValidator.requireAccountId(accountId);

        log.info("Calculando uso do plano por accountId. accountId={}", accountId);

        Account account = publicSchemaUnitOfWork.readOnly(() ->
                accountRepository.findByIdAndDeletedFalse(accountId)
                        .orElseThrow(() -> new ApiException(
                                ApiErrorCode.ACCOUNT_NOT_FOUND,
                                "Conta não encontrada com id: " + accountId
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
                "Calculando uso do plano a partir da conta já resolvida. accountId={}, currentPlan={}, builtIn={}",
                account.getId(),
                account.getSubscriptionPlan(),
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

        AccountUsageSnapshot materializedSnapshot =
                accountUsageSnapshotQueryService.requireByAccountId(account.getId());

        PlanUsageSnapshot snapshot = new PlanUsageSnapshot(
                account.getId(),
                account.getSubscriptionPlan(),
                materializedSnapshot.getCurrentUsers(),
                materializedSnapshot.getCurrentProducts(),
                materializedSnapshot.getCurrentStorageMb()
        );

        log.info(
                "Uso do plano calculado com sucesso a partir de snapshot público. accountId={}, currentPlan={}, users={}, products={}, storageMb={}, measuredAt={}",
                snapshot.accountId(),
                snapshot.currentPlan(),
                snapshot.currentUsers(),
                snapshot.currentProducts(),
                snapshot.currentStorageMb(),
                materializedSnapshot.getMeasuredAt()
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
        SubscriptionValidator.requireAccountId(accountId);

        log.info("Calculando uso do plano para conta operacional. accountId={}", accountId);

        Account account = publicSchemaUnitOfWork.readOnly(() ->
                accountRepository.findEnabledById(accountId)
                        .orElseThrow(() -> new ApiException(
                                ApiErrorCode.ACCOUNT_NOT_FOUND,
                                "Conta operacional não encontrada com id: " + accountId
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
        SubscriptionValidator.requireAccount(account);
        SubscriptionValidator.requireAccountId(account.getId());
        SubscriptionValidator.requireSubscriptionPlan(account.getSubscriptionPlan());
    }
}