package brito.com.multitenancy001.tenant.subscription.app;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.executor.TenantToPublicBridgeExecutor;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountEntitlementsGuard;
import brito.com.multitenancy001.tenant.products.persistence.TenantProductRepository;
import brito.com.multitenancy001.tenant.users.persistence.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Serviço centralizado de enforcement de quotas no contexto tenant.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Calcular uso atual real no schema tenant.</li>
 *   <li>Executar bridge explícito TENANT -> PUBLIC para validar entitlements.</li>
 *   <li>Garantir fail-fast antes de operações críticas de escrita.</li>
 * </ul>
 *
 * <p>Regra arquitetural:</p>
 * <ul>
 *   <li>Este service deve ser chamado no write-path canônico (create user / create product).</li>
 *   <li>Controllers e services de current-context não devem ser a última linha de defesa.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantQuotaEnforcementService {

    private final TenantUserRepository tenantUserRepository;
    private final TenantProductRepository tenantProductRepository;
    private final AccountEntitlementsGuard accountEntitlementsGuard;
    private final TenantToPublicBridgeExecutor tenantToPublicBridgeExecutor;

    /**
     * Valida se a conta ainda pode criar usuários.
     *
     * @param accountId id da conta
     */
    public void assertCanCreateUser(Long accountId) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        }

        long currentUsers = tenantUserRepository.countByAccountIdAndDeletedFalse(accountId);

        log.info("Validando quota para criação de usuário. accountId={}, currentUsers={}",
                accountId, currentUsers);

        tenantToPublicBridgeExecutor.run(() ->
                accountEntitlementsGuard.assertCanCreateUser(accountId, currentUsers)
        );

        log.info("Quota de usuários validada com sucesso. accountId={}, currentUsers={}",
                accountId, currentUsers);
    }

    /**
     * Valida se a conta ainda pode criar produtos.
     *
     * @param accountId id da conta
     */
    public void assertCanCreateProduct(Long accountId) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        }

        long currentProducts = tenantProductRepository.countByDeletedFalse();

        log.info("Validando quota para criação de produto. accountId={}, currentProducts={}",
                accountId, currentProducts);

        tenantToPublicBridgeExecutor.run(() ->
                accountEntitlementsGuard.assertCanCreateProduct(accountId, currentProducts)
        );

        log.info("Quota de produtos validada com sucesso. accountId={}, currentProducts={}",
                accountId, currentProducts);
    }
}