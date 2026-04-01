package brito.com.multitenancy001.tenant.subscription.app;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.products.persistence.TenantProductRepository;
import brito.com.multitenancy001.tenant.subscription.app.dto.TenantUsageMeasurement;
import brito.com.multitenancy001.tenant.users.persistence.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de aplicação do contexto Tenant responsável por medir o uso atual
 * dos recursos relevantes para subscription e enforcement de quota.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Contar usuários habilitados do tenant.</li>
 *   <li>Contar produtos não deletados do tenant.</li>
 *   <li>Centralizar a semântica de medição de uso do contexto Tenant.</li>
 * </ul>
 *
 * <p>Regras arquiteturais:</p>
 * <ul>
 *   <li>Este service assume que o contexto tenant já foi corretamente bindado.</li>
 *   <li>Este service não faz schema switch.</li>
 *   <li>Este service não acessa Public Schema.</li>
 *   <li>Este service usa apenas repositories do próprio contexto Tenant.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantUsageMeasurementService {

    private final TenantUserRepository tenantUserRepository;
    private final TenantProductRepository tenantProductRepository;

    /**
     * Mede o uso atual do tenant para a conta informada.
     *
     * @param accountId id da conta
     * @return snapshot interno de uso do tenant
     */
    public TenantUsageMeasurement measureUsage(Long accountId) {
        validateAccountId(accountId);

        long currentUsers = tenantUserRepository.countEnabledUsersByAccount(accountId);
        long currentProducts = tenantProductRepository.countByDeletedFalse();

        log.info(
                "Uso do tenant medido com sucesso. accountId={}, currentUsers={}, currentProducts={}",
                accountId,
                currentUsers,
                currentProducts
        );

        return new TenantUsageMeasurement(
                currentUsers,
                currentProducts
        );
    }

    /**
     * Mede apenas a quantidade atual de usuários habilitados.
     *
     * @param accountId id da conta
     * @return quantidade atual de usuários habilitados
     */
    public long measureCurrentUsers(Long accountId) {
        validateAccountId(accountId);

        long currentUsers = tenantUserRepository.countEnabledUsersByAccount(accountId);

        log.debug(
                "Quantidade atual de usuários habilitados medida. accountId={}, currentUsers={}",
                accountId,
                currentUsers
        );

        return currentUsers;
    }

    /**
     * Mede apenas a quantidade atual de produtos não deletados.
     *
     * @return quantidade atual de produtos não deletados
     */
    public long measureCurrentProducts() {
        long currentProducts = tenantProductRepository.countByDeletedFalse();

        log.debug(
                "Quantidade atual de produtos não deletados medida. currentProducts={}",
                currentProducts
        );

        return currentProducts;
    }

    /**
     * Valida o identificador da conta.
     *
     * @param accountId id da conta
     */
    private void validateAccountId(Long accountId) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        }
    }
}