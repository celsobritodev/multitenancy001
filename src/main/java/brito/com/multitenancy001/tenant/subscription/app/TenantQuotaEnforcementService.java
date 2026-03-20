package brito.com.multitenancy001.tenant.subscription.app;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.infrastructure.tenant.TenantSchemaUnitOfWork;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.executor.TenantToPublicBridgeExecutor;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountEntitlementsGuard;
import brito.com.multitenancy001.tenant.products.persistence.TenantProductRepository;
import brito.com.multitenancy001.tenant.users.persistence.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço centralizado de enforcement de quotas no contexto tenant.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Medir uso real no schema tenant.</li>
 *   <li>Executar validação de entitlements no PUBLIC de forma explícita.</li>
 *   <li>Evitar crossing PUBLIC dentro de transação tenant de escrita.</li>
 * </ul>
 *
 * <p>Regra arquitetural:</p>
 * <ul>
 *   <li>O uso tenant é medido em readOnly tenant.</li>
 *   <li>A validação PUBLIC é executada fora da transação tenant de escrita.</li>
 *   <li>Este service deve ser chamado antes do bloco principal de save em write-paths críticos.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantQuotaEnforcementService {

    private final TenantSchemaUnitOfWork tenantSchemaUnitOfWork;
    private final TenantUserRepository tenantUserRepository;
    private final TenantProductRepository tenantProductRepository;
    private final AccountEntitlementsGuard accountEntitlementsGuard;
    private final TenantToPublicBridgeExecutor tenantToPublicBridgeExecutor;

    /**
     * Mede o uso atual de usuários no tenant e valida a quota correspondente no PUBLIC.
     *
     * @param accountId id da conta
     * @param tenantSchema schema do tenant
     */
    public void assertCanCreateUser(Long accountId, String tenantSchema) {
        validateInputs(accountId, tenantSchema);

        long currentUsers = tenantSchemaUnitOfWork.readOnly(tenantSchema, () ->
                tenantUserRepository.countByAccountIdAndDeletedFalse(accountId)
        );

        log.info(
                "Validando quota para criação de usuário. accountId={}, tenantSchema={}, currentUsers={}",
                accountId,
                tenantSchema,
                currentUsers
        );

        tenantToPublicBridgeExecutor.run(() ->
                accountEntitlementsGuard.assertCanCreateUser(accountId, currentUsers)
        );

        log.info(
                "Quota de usuários validada com sucesso. accountId={}, tenantSchema={}, currentUsers={}",
                accountId,
                tenantSchema,
                currentUsers
        );
    }

    /**
     * Mede o uso atual de produtos no tenant e valida a quota correspondente no PUBLIC.
     *
     * @param accountId id da conta
     * @param tenantSchema schema do tenant
     */
    public void assertCanCreateProduct(Long accountId, String tenantSchema) {
        validateInputs(accountId, tenantSchema);

        long currentProducts = tenantSchemaUnitOfWork.readOnly(tenantSchema, tenantProductRepository::countByDeletedFalse);

        log.info(
                "Validando quota para criação de produto. accountId={}, tenantSchema={}, currentProducts={}",
                accountId,
                tenantSchema,
                currentProducts
        );

        tenantToPublicBridgeExecutor.run(() ->
                accountEntitlementsGuard.assertCanCreateProduct(accountId, currentProducts)
        );

        log.info(
                "Quota de produtos validada com sucesso. accountId={}, tenantSchema={}, currentProducts={}",
                accountId,
                tenantSchema,
                currentProducts
        );
    }

    /**
     * Valida parâmetros obrigatórios.
     *
     * @param accountId id da conta
     * @param tenantSchema schema do tenant
     */
    private void validateInputs(Long accountId, String tenantSchema) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        }

        if (tenantSchema == null || tenantSchema.isBlank()) {
            throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatório", 400);
        }
    }
}