package brito.com.multitenancy001.shared.persistence.publicschema;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Guard de entitlements/quota no Public Schema.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Carregar a Account pública associada ao tenant.</li>
 *   <li>Executar asserts de quota de forma centralizada.</li>
 *   <li>Concentrar logs e fail-fast de boundary no lado PUBLIC.</li>
 * </ul>
 *
 * <p>Observação importante:</p>
 * <ul>
 *   <li>Este guard continua usando TX PUBLIC normal porque o serviço de entitlements
 *       pode provisionar defaults de forma idempotente.</li>
 *   <li>O bridge TENANT -> PUBLIC continua sendo responsabilidade do chamador.</li>
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
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        }

        if (currentUsers < 0) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "currentUsers não pode ser negativo", 400);
        }

        log.info("Iniciando assert de quota de usuários no PUBLIC. accountId={}, currentUsers={}",
                accountId, currentUsers);

        publicSchemaUnitOfWork.tx(() -> {
            Account account = loadAccountOrThrow(accountId);
            accountEntitlementsService.assertCanCreateUser(account, currentUsers);
        });

        log.info("Assert de quota de usuários concluído com sucesso no PUBLIC. accountId={}, currentUsers={}",
                accountId, currentUsers);
    }

    /**
     * Valida quota de criação de produtos.
     *
     * @param accountId id da conta
     * @param currentProducts uso atual já medido no tenant
     */
    public void assertCanCreateProduct(Long accountId, long currentProducts) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        }

        if (currentProducts < 0) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "currentProducts não pode ser negativo", 400);
        }

        log.info("Iniciando assert de quota de produtos no PUBLIC. accountId={}, currentProducts={}",
                accountId, currentProducts);

        publicSchemaUnitOfWork.tx(() -> {
            Account account = loadAccountOrThrow(accountId);
            accountEntitlementsService.assertCanCreateProduct(account, currentProducts);
        });

        log.info("Assert de quota de produtos concluído com sucesso no PUBLIC. accountId={}, currentProducts={}",
                accountId, currentProducts);
    }

    /**
     * Resolve snapshot efetivo de entitlements.
     *
     * @param accountId id da conta
     * @return snapshot efetivo
     */
    public AccountEntitlementsSnapshot resolveEffectiveSnapshot(Long accountId) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        }

        log.info("Resolvendo snapshot efetivo de entitlements. accountId={}", accountId);

        AccountEntitlementsSnapshot snapshot = publicSchemaUnitOfWork.tx(() -> {
            Account account = loadAccountOrThrow(accountId);
            return accountEntitlementsService.resolveEffective(account);
        });

        log.info("Snapshot efetivo de entitlements resolvido com sucesso. accountId={}, unlimited={}, maxUsers={}, maxProducts={}, maxStorageMb={}",
                accountId,
                snapshot.unlimited(),
                snapshot.maxUsers(),
                snapshot.maxProducts(),
                snapshot.maxStorageMb());

        return snapshot;
    }

    private Account loadAccountOrThrow(Long accountId) {
        return accountRepository.findByIdAndDeletedFalse(accountId)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.ACCOUNT_NOT_FOUND,
                        "Conta não encontrada",
                        404
                ));
    }
}