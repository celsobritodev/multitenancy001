package brito.com.multitenancy001.tenant.subscription.app;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.infrastructure.tenant.TenantSchemaUnitOfWork;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.executor.TenantToPublicBridgeExecutor;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountEntitlementsGuard;
import brito.com.multitenancy001.tenant.subscription.app.dto.TenantUsageMeasurement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço centralizado de enforcement de quotas no contexto Tenant.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Medir o uso real no schema tenant.</li>
 *   <li>Executar validação de entitlements no Public Schema de forma explícita.</li>
 *   <li>Evitar crossing Public dentro da mesma transação tenant de escrita.</li>
 *   <li>Garantir coerência entre medição de uso, limits, enforcement e sincronização de snapshot.</li>
 * </ul>
 *
 * <p>Regras arquiteturais:</p>
 * <ul>
 *   <li>O uso tenant é medido em {@code readOnly} no schema tenant.</li>
 *   <li>A validação Public é executada fora da transação tenant de escrita.</li>
 *   <li>Este service deve ser chamado antes do save principal em write-paths críticos.</li>
 *   <li>As métricas de uso permanecem alinhadas com {@link TenantUsageMeasurementService}.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantQuotaEnforcementService {

    private final TenantSchemaUnitOfWork tenantSchemaUnitOfWork;
    private final TenantUsageMeasurementService tenantUsageMeasurementService;
    private final AccountEntitlementsGuard accountEntitlementsGuard;
    private final TenantToPublicBridgeExecutor tenantToPublicBridgeExecutor;

    /**
     * Mede o uso atual de usuários habilitados no tenant
     * e valida a quota correspondente no Public Schema.
     *
     * @param accountId id da conta
     * @param tenantSchema schema do tenant
     */
    public void assertCanCreateUser(Long accountId, String tenantSchema) {
        validateInputs(accountId, tenantSchema);

        String normalizedTenantSchema = normalizeTenantSchema(tenantSchema);

        log.info(
                "Iniciando enforcement de quota para criação de usuário. accountId={}, tenantSchema={}",
                accountId,
                normalizedTenantSchema
        );

        long currentUsers = tenantSchemaUnitOfWork.readOnly(
                normalizedTenantSchema,
                () -> tenantUsageMeasurementService.measureCurrentUsers(accountId)
        );

        log.info(
                "Uso real de usuários medido para enforcement. accountId={}, tenantSchema={}, currentUsers={}",
                accountId,
                normalizedTenantSchema,
                currentUsers
        );

        tenantToPublicBridgeExecutor.run(() -> {
            log.info(
                    "Executando validação Public de quota de usuários. accountId={}, currentUsers={}",
                    accountId,
                    currentUsers
            );
            accountEntitlementsGuard.assertCanCreateUser(accountId, currentUsers);
        });

        log.info(
                "Quota de usuários validada com sucesso. accountId={}, tenantSchema={}, currentUsers={}",
                accountId,
                normalizedTenantSchema,
                currentUsers
        );
    }

    /**
     * Mede o uso atual de produtos não deletados no tenant
     * e valida a quota correspondente no Public Schema.
     *
     * @param accountId id da conta
     * @param tenantSchema schema do tenant
     */
    public void assertCanCreateProduct(Long accountId, String tenantSchema) {
        validateInputs(accountId, tenantSchema);

        String normalizedTenantSchema = normalizeTenantSchema(tenantSchema);

        log.info(
                "Iniciando enforcement de quota para criação de produto. accountId={}, tenantSchema={}",
                accountId,
                normalizedTenantSchema
        );

        long currentProducts = tenantSchemaUnitOfWork.readOnly(
                normalizedTenantSchema,
                () -> tenantUsageMeasurementService.measureCurrentProducts(accountId)
        );

        log.info(
                "Uso real de produtos medido para enforcement. accountId={}, tenantSchema={}, currentProducts={}",
                accountId,
                normalizedTenantSchema,
                currentProducts
        );

        tenantToPublicBridgeExecutor.run(() -> {
            log.info(
                    "Executando validação Public de quota de produtos. accountId={}, currentProducts={}",
                    accountId,
                    currentProducts
            );
            accountEntitlementsGuard.assertCanCreateProduct(accountId, currentProducts);
        });

        log.info(
                "Quota de produtos validada com sucesso. accountId={}, tenantSchema={}, currentProducts={}",
                accountId,
                normalizedTenantSchema,
                currentProducts
        );
    }

    /**
     * Mede o uso completo atual do tenant.
     *
     * @param accountId id da conta
     * @param tenantSchema schema do tenant
     * @return medição consolidada
     */
    public TenantUsageMeasurement measureUsage(Long accountId, String tenantSchema) {
        validateInputs(accountId, tenantSchema);

        String normalizedTenantSchema = normalizeTenantSchema(tenantSchema);

        log.info(
                "Iniciando medição completa de uso. accountId={}, tenantSchema={}",
                accountId,
                normalizedTenantSchema
        );

        TenantUsageMeasurement measurement = tenantSchemaUnitOfWork.readOnly(
                normalizedTenantSchema,
                () -> tenantUsageMeasurementService.measureUsage(accountId)
        );

        log.info(
                "Medição completa de uso concluída. accountId={}, tenantSchema={}, currentUsers={}, currentProducts={}",
                accountId,
                normalizedTenantSchema,
                measurement.currentUsers(),
                measurement.currentProducts()
        );

        return measurement;
    }

    /**
     * Valida entradas obrigatórias.
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
     * Normaliza o schema do tenant.
     *
     * @param tenantSchema schema bruto
     * @return schema normalizado
     */
    private String normalizeTenantSchema(String tenantSchema) {
        String normalizedTenantSchema = tenantSchema == null ? null : tenantSchema.trim();

        if (!StringUtils.hasText(normalizedTenantSchema)) {
            throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatório", 400);
        }

        return normalizedTenantSchema;
    }
}