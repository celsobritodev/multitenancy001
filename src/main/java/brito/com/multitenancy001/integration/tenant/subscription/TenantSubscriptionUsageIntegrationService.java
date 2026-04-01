package brito.com.multitenancy001.integration.tenant.subscription;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.infrastructure.tenant.TenantExecutor;
import brito.com.multitenancy001.integration.tenant.dto.TenantUsageSnapshot;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.subscription.app.TenantUsageMeasurementService;
import brito.com.multitenancy001.tenant.subscription.app.dto.TenantUsageMeasurement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Camada explícita de integração entre Control Plane e Tenant
 * para medição de uso de subscription/quota.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Executar o schema switch para o tenant alvo.</li>
 *   <li>Delegar a medição real para o contexto Tenant.</li>
 *   <li>Retornar snapshot simples para consumo cross-boundary.</li>
 * </ul>
 *
 * <p>Regras arquiteturais:</p>
 * <ul>
 *   <li>Control Plane não usa repositories tenant diretamente.</li>
 *   <li>O crossing de boundary fica centralizado nesta integração.</li>
 *   <li>A lógica de medição permanece no contexto Tenant.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantSubscriptionUsageIntegrationService {

    private final TenantExecutor tenantExecutor;
    private final TenantUsageMeasurementService tenantUsageMeasurementService;

    /**
     * Mede o uso atual do tenant para a conta informada.
     *
     * @param tenantSchema schema do tenant
     * @param accountId id da conta
     * @return snapshot de uso consumível fora do contexto Tenant
     */
    public TenantUsageSnapshot measureUsage(String tenantSchema, Long accountId) {
        validateInputs(tenantSchema, accountId);

        String normalizedTenantSchema = normalizeTenantSchema(tenantSchema);

        log.info(
                "Iniciando medição de uso via integração tenant. accountId={}, tenantSchema={}",
                accountId,
                normalizedTenantSchema
        );

        TenantUsageMeasurement measurement = tenantExecutor.runInTenantSchema(
                normalizedTenantSchema,
                () -> tenantUsageMeasurementService.measureUsage(accountId)
        );

        TenantUsageSnapshot snapshot = new TenantUsageSnapshot(
                measurement.currentUsers(),
                measurement.currentProducts()
        );

        log.info(
                "Medição de uso via integração tenant concluída com sucesso. accountId={}, tenantSchema={}, currentUsers={}, currentProducts={}",
                accountId,
                normalizedTenantSchema,
                snapshot.currentUsers(),
                snapshot.currentProducts()
        );

        return snapshot;
    }

    /**
     * Valida os parâmetros obrigatórios da integração.
     *
     * @param tenantSchema schema do tenant
     * @param accountId id da conta
     */
    private void validateInputs(String tenantSchema, Long accountId) {
        if (!StringUtils.hasText(tenantSchema)) {
            throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatório", 400);
        }

        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
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