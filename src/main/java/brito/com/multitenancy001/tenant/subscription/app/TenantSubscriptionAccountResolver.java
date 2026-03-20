package brito.com.multitenancy001.tenant.subscription.app;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.integration.security.TenantRequestIdentityService;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.executor.TenantToPublicBridgeExecutor;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Resolver centralizado para acesso a Account no boundary TENANT -> PUBLIC.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Resolver o accountId da identidade autenticada no contexto tenant.</li>
 *   <li>Executar bridge explícito TENANT -> PUBLIC.</li>
 *   <li>Carregar a Account no Public Schema sem vazar essa lógica para services de feature.</li>
 * </ul>
 *
 * <p>Objetivo arquitetural:</p>
 * <ul>
 *   <li>Reduzir boundary leak no TenantSubscriptionQueryService e em outros services tenant.</li>
 *   <li>Padronizar crossing TENANT -> PUBLIC em um ponto único e auditável.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantSubscriptionAccountResolver {

    private final TenantRequestIdentityService requestIdentity;
    private final TenantToPublicBridgeExecutor tenantToPublicBridgeExecutor;
    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final AccountRepository accountRepository;

    /**
     * Resolve a Account da identidade autenticada no contexto tenant.
     *
     * @return account do tenant autenticado
     */
    public Account resolveCurrentAccount() {
        Long accountId = requestIdentity.getCurrentAccountId();

        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        }

        log.info("Resolvendo conta atual do tenant autenticado. accountId={}", accountId);

        Account account = tenantToPublicBridgeExecutor.call(() ->
                publicSchemaUnitOfWork.readOnly(() ->
                        accountRepository.findByIdAndDeletedFalse(accountId)
                                .orElseThrow(() -> new ApiException(
                                        ApiErrorCode.ACCOUNT_NOT_FOUND,
                                        "Conta não encontrada",
                                        404
                                ))
                )
        );

        log.info("Conta atual resolvida com sucesso. accountId={}, plan={}, status={}",
                account.getId(),
                account.getSubscriptionPlan(),
                account.getStatus());

        return account;
    }
}