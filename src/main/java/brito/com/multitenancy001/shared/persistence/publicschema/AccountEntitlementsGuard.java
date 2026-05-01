package brito.com.multitenancy001.shared.persistence.publicschema;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.validation.RequiredValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Ponto central de enforcement de quotas e limites de conta, operando no schema PUBLIC.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountEntitlementsGuard {

    private final AccountRepository accountRepository;
    private final AccountEntitlementsService accountEntitlementsService;
    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;

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

    public AccountEntitlementsSnapshot resolveEffectiveSnapshot(Long accountId) {
        RequiredValidator.requirePayload(
                accountId,
                ApiErrorCode.ACCOUNT_REQUIRED,
                "accountId é obrigatório"
        );

        log.info("Resolvendo snapshot efetivo de entitlements no guard. accountId={}", accountId);

        AccountEntitlementsSnapshot snapshot =
                accountEntitlementsService.resolveEffectiveByAccountId(accountId);

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

    private Account loadAccountOrThrow(Long accountId) {
        return accountRepository.findByIdAndDeletedFalse(accountId)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.ACCOUNT_NOT_FOUND,
                        "Conta não encontrada"
                ));
    }

    private void validateInputs(Long accountId, long currentUsage, String usageField) {
        RequiredValidator.requirePayload(
                accountId,
                ApiErrorCode.ACCOUNT_REQUIRED,
                "accountId é obrigatório"
        );

        if (currentUsage < 0) {
            throw new ApiException(
                    ApiErrorCode.INVALID_REQUEST,
                    usageField + " não pode ser negativo"
            );
        }
    }
}