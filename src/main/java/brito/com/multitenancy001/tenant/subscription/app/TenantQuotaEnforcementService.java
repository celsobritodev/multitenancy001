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
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantQuotaEnforcementService {

    private final TenantSchemaUnitOfWork tenantSchemaUnitOfWork;
    private final TenantUsageMeasurementService tenantUsageMeasurementService;
    private final AccountEntitlementsGuard accountEntitlementsGuard;
    private final TenantToPublicBridgeExecutor tenantToPublicBridgeExecutor;

    public void assertCanCreateUser(Long accountId, String tenantSchema) {
        validateInputs(accountId, tenantSchema);

        String normalizedTenantSchema = normalizeTenantSchema(tenantSchema);

        log.info(
                "Enforcement USER START accountId={} tenantSchema={}",
                accountId,
                normalizedTenantSchema
        );

        TenantUsageMeasurement measurement = tenantSchemaUnitOfWork.readOnly(
                normalizedTenantSchema,
                () -> tenantUsageMeasurementService.measureUsage(accountId)
        );

        long currentUsers = measurement.currentUsers();

        tenantToPublicBridgeExecutor.run(() ->
                accountEntitlementsGuard.assertCanCreateUser(accountId, currentUsers)
        );

        log.info(
                "Enforcement USER OK accountId={} users={}",
                accountId,
                currentUsers
        );
    }

    public void assertCanCreateProduct(Long accountId, String tenantSchema) {
        validateInputs(accountId, tenantSchema);

        String normalizedTenantSchema = normalizeTenantSchema(tenantSchema);

        log.info(
                "Enforcement PRODUCT START accountId={} tenantSchema={}",
                accountId,
                normalizedTenantSchema
        );

        TenantUsageMeasurement measurement = tenantSchemaUnitOfWork.readOnly(
                normalizedTenantSchema,
                () -> tenantUsageMeasurementService.measureUsage(accountId)
        );

        long currentProducts = measurement.currentProducts();

        tenantToPublicBridgeExecutor.run(() ->
                accountEntitlementsGuard.assertCanCreateProduct(accountId, currentProducts)
        );

        log.info(
                "Enforcement PRODUCT OK accountId={} products={}",
                accountId,
                currentProducts
        );
    }

    public TenantUsageMeasurement measureUsage(Long accountId, String tenantSchema) {
        validateInputs(accountId, tenantSchema);

        String normalizedTenantSchema = normalizeTenantSchema(tenantSchema);

        return tenantSchemaUnitOfWork.readOnly(
                normalizedTenantSchema,
                () -> tenantUsageMeasurementService.measureUsage(accountId)
        );
    }

    private void validateInputs(Long accountId, String tenantSchema) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        }
        if (!StringUtils.hasText(tenantSchema)) {
            throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatório", 400);
        }
    }

    private String normalizeTenantSchema(String tenantSchema) {
        return tenantSchema.trim();
    }
}