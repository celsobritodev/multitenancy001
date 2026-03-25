package brito.com.multitenancy001.shared.persistence.publicschema;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Guard central de quota e entitlements no PUBLIC schema.
 *
 * <p><b>Responsabilidades:</b></p>
 * <ul>
 *   <li>Carregar a conta pública associada ao tenant.</li>
 *   <li>Executar asserts de quota com logs de diagnóstico.</li>
 *   <li>Concentrar o boundary PUBLIC para enforcement de hard limits.</li>
 *   <li>Expor snapshot efetivo de entitlements para troubleshooting.</li>
 * </ul>
 *
 * <p><b>Diretriz arquitetural:</b></p>
 * <ul>
 *   <li>O bridge TENANT -&gt; PUBLIC continua sendo responsabilidade do chamador.</li>
 *   <li>Este guard deve permanecer enxuto e delegar a regra ao
 *       {@link AccountEntitlementsService}.</li>
 *   <li>Os logs deste guard são parte do monitoramento da V30.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountEntitlementsGuard {

    private final AccountRepository accountRepository;
    private final AccountEntitlementsService accountEntitlementsService;
    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;

    /**
     * Valida quota de criação de usuários.
     *
     * @param accountId id da conta
     * @param currentUsers uso atual já medido no tenant
     */
    public void assertCanCreateUser(Long accountId, long currentUsers) {
        validateInputs(accountId, currentUsers, "currentUsers");

        log.info(
                "Iniciando assert de quota de usuários no PUBLIC. accountId={}, currentUsers={}",
                accountId,
                currentUsers
        );

        Account account = publicSchemaUnitOfWork.readOnly(() -> loadAccountOrThrow(accountId));
        AccountEntitlementsSnapshot snapshot = accountEntitlementsService.resolveEffective(account);

        log.info(
                "Snapshot efetivo antes do assert de usuários. accountId={}, plan={}, unlimited={}, maxUsers={}, maxProducts={}, maxStorageMb={}, currentUsers={}",
                accountId,
                account.getSubscriptionPlan(),
                snapshot.unlimited(),
                snapshot.maxUsers(),
                snapshot.maxProducts(),
                snapshot.maxStorageMb(),
                currentUsers
        );

        accountEntitlementsService.assertCanCreateUser(account, currentUsers);

        log.info(
                "Assert de quota de usuários concluído com sucesso no PUBLIC. accountId={}, currentUsers={}",
                accountId,
                currentUsers
        );
    }

    /**
     * Valida quota de criação de produtos.
     *
     * @param accountId id da conta
     * @param currentProducts uso atual já medido no tenant
     */
    public void assertCanCreateProduct(Long accountId, long currentProducts) {
        validateInputs(accountId, currentProducts, "currentProducts");

        log.info(
                "Iniciando assert de quota de produtos no PUBLIC. accountId={}, currentProducts={}",
                accountId,
                currentProducts
        );

        Account account = publicSchemaUnitOfWork.readOnly(() -> loadAccountOrThrow(accountId));
        AccountEntitlementsSnapshot snapshot = accountEntitlementsService.resolveEffective(account);

        log.info(
                "Snapshot efetivo antes do assert de produtos. accountId={}, plan={}, unlimited={}, maxUsers={}, maxProducts={}, maxStorageMb={}, currentProducts={}",
                accountId,
                account.getSubscriptionPlan(),
                snapshot.unlimited(),
                snapshot.maxUsers(),
                snapshot.maxProducts(),
                snapshot.maxStorageMb(),
                currentProducts
        );

        accountEntitlementsService.assertCanCreateProduct(account, currentProducts);

        log.info(
                "Assert de quota de produtos concluído com sucesso no PUBLIC. accountId={}, currentProducts={}",
                accountId,
                currentProducts
        );
    }

    /**
     * Resolve o snapshot efetivo de entitlements para diagnóstico.
     *
     * @param accountId id da conta
     * @return snapshot efetivo
     */
    public AccountEntitlementsSnapshot resolveEffectiveSnapshot(Long accountId) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        }

        log.info("Resolvendo snapshot efetivo de entitlements no guard. accountId={}", accountId);

        AccountEntitlementsSnapshot snapshot = accountEntitlementsService.resolveEffectiveByAccountId(accountId);

        log.info(
                "Snapshot efetivo resolvido com sucesso no guard. accountId={}, unlimited={}, maxUsers={}, maxProducts={}, maxStorageMb={}",
                accountId,
                snapshot.unlimited(),
                snapshot.maxUsers(),
                snapshot.maxProducts(),
                snapshot.maxStorageMb()
        );

        return snapshot;
    }

    /**
     * Carrega a conta pública ativa e não deletada.
     *
     * @param accountId id da conta
     * @return conta encontrada
     */
    private Account loadAccountOrThrow(Long accountId) {
        return accountRepository.findByIdAndDeletedFalse(accountId)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.ACCOUNT_NOT_FOUND,
                        "Conta não encontrada",
                        404
                ));
    }

    /**
     * Valida parâmetros básicos do guard.
     *
     * @param accountId id da conta
     * @param currentUsage uso atual
     * @param usageField nome lógico do campo de uso
     */
    private void validateInputs(Long accountId, long currentUsage, String usageField) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        }

        if (currentUsage < 0) {
            throw new ApiException(
                    ApiErrorCode.INVALID_REQUEST,
                    usageField + " não pode ser negativo",
                    400
            );
        }
    }
}