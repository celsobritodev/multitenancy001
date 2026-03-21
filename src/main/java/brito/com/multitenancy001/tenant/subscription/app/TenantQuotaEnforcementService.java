package brito.com.multitenancy001.tenant.subscription.app;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
 * <p><b>Responsabilidades:</b></p>
 * <ul>
 *   <li>Medir uso real no schema tenant.</li>
 *   <li>Executar validação de entitlements no PUBLIC de forma explícita.</li>
 *   <li>Evitar crossing PUBLIC dentro da transação tenant de escrita.</li>
 *   <li>Garantir coerência entre medição de uso e cálculo exposto em endpoints de limits.</li>
 * </ul>
 *
 * <p><b>Regras arquiteturais:</b></p>
 * <ul>
 *   <li>O uso tenant é medido em {@code readOnly} no schema tenant.</li>
 *   <li>A validação PUBLIC é executada fora da transação tenant de escrita.</li>
 *   <li>Este service deve ser chamado antes do bloco principal de save em write-paths críticos.</li>
 *   <li>As métricas de uso devem permanecer alinhadas com {@code AccountPlanUsageService}.</li>
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
     * Mede o uso atual de usuários habilitados no tenant e valida a quota correspondente no PUBLIC.
     *
     * <p>Este método usa a mesma semântica de contagem do snapshot de uso do plano,
     * evitando divergência entre enforcement e endpoint de limits.</p>
     *
     * @param accountId id da conta
     * @param tenantSchema schema do tenant
     */
    public void assertCanCreateUser(Long accountId, String tenantSchema) {
        validateInputs(accountId, tenantSchema);

        String normalizedTenantSchema = normalizeTenantSchema(tenantSchema);

        long currentUsers = tenantSchemaUnitOfWork.readOnly(
                normalizedTenantSchema,
                () -> tenantUserRepository.countEnabledUsersByAccount(accountId)
        );

        log.info(
                "Validando quota para criação de usuário. accountId={}, tenantSchema={}, currentUsers={}",
                accountId,
                normalizedTenantSchema,
                currentUsers
        );

        tenantToPublicBridgeExecutor.run(() ->
                accountEntitlementsGuard.assertCanCreateUser(accountId, currentUsers)
        );

        log.info(
                "Quota de usuários validada com sucesso. accountId={}, tenantSchema={}, currentUsers={}",
                accountId,
                normalizedTenantSchema,
                currentUsers
        );
    }

    /**
     * Mede o uso atual de produtos não deletados no tenant e valida a quota correspondente no PUBLIC.
     *
     * <p>Este método usa a mesma semântica de contagem do snapshot de uso do plano,
     * evitando divergência entre enforcement e endpoint de limits.</p>
     *
     * @param accountId id da conta
     * @param tenantSchema schema do tenant
     */
    public void assertCanCreateProduct(Long accountId, String tenantSchema) {
        validateInputs(accountId, tenantSchema);

        String normalizedTenantSchema = normalizeTenantSchema(tenantSchema);

        long currentProducts = tenantSchemaUnitOfWork.readOnly(
                normalizedTenantSchema,
                tenantProductRepository::countByDeletedFalse
        );

        log.info(
                "Validando quota para criação de produto. accountId={}, tenantSchema={}, currentProducts={}",
                accountId,
                normalizedTenantSchema,
                currentProducts
        );

        tenantToPublicBridgeExecutor.run(() ->
                accountEntitlementsGuard.assertCanCreateProduct(accountId, currentProducts)
        );

        log.info(
                "Quota de produtos validada com sucesso. accountId={}, tenantSchema={}, currentProducts={}",
                accountId,
                normalizedTenantSchema,
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

        if (!StringUtils.hasText(tenantSchema)) {
            throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatório", 400);
        }
    }

    /**
     * Normaliza e valida o schema tenant informado.
     *
     * @param tenantSchema schema bruto
     * @return schema normalizado
     */
    private String normalizeTenantSchema(String tenantSchema) {
        if (!StringUtils.hasText(tenantSchema)) {
            throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatório", 400);
        }

        return tenantSchema.trim();
    }
}