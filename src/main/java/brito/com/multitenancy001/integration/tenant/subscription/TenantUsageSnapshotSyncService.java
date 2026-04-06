package brito.com.multitenancy001.integration.tenant.subscription;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.controlplane.accounts.app.subscription.AccountUsageSnapshotUpsertService;
import brito.com.multitenancy001.infrastructure.tenant.TenantContextExecutor;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.subscription.app.TenantUsageMeasurementService;
import brito.com.multitenancy001.tenant.subscription.app.dto.TenantUsageMeasurement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de integração responsável por sincronizar o uso real do tenant
 * para o snapshot materializado no schema public.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Executar schema switch controlado para o tenant alvo.</li>
 *   <li>Delegar a medição real ao contexto Tenant.</li>
 *   <li>Persistir/atualizar o snapshot público de uso da conta.</li>
 *   <li>Centralizar logs, validações e semântica de sincronização cross-boundary.</li>
 * </ul>
 *
 * <p>Regras arquiteturais:</p>
 * <ul>
 *   <li>A medição continua no contexto Tenant.</li>
 *   <li>O snapshot público é a fonte de verdade consumida pelo Control Plane.</li>
 *   <li>Este service não executa decisão de negócio de subscription; apenas sincroniza estado materializado.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantUsageSnapshotSyncService {

    private final TenantContextExecutor tenantContextExecutor;
    private final TenantUsageMeasurementService tenantUsageMeasurementService;
    private final AccountUsageSnapshotUpsertService accountUsageSnapshotUpsertService;
    private final AppClock appClock;

    /**
     * Mede o uso real do tenant e sincroniza o snapshot público da conta.
     *
     * @param tenantSchema schema do tenant
     * @param accountId id da conta
     */
    public void syncUsageSnapshot(String tenantSchema, Long accountId) {
        validateInputs(tenantSchema, accountId);

        String normalizedTenantSchema = normalizeTenantSchema(tenantSchema);

        log.info(
                "Iniciando sincronização de usage snapshot. accountId={}, tenantSchema={}",
                accountId,
                normalizedTenantSchema
        );

        TenantUsageMeasurement measurement = tenantContextExecutor.runInTenantSchema(
                normalizedTenantSchema,
                () -> tenantUsageMeasurementService.measureUsage(accountId)
        );

        Instant measuredAt = appClock.instant();

        accountUsageSnapshotUpsertService.upsert(
                accountId,
                measurement.currentUsers(),
                measurement.currentProducts(),
                0L,
                measuredAt
        );

        log.info(
                "Sincronização de usage snapshot concluída com sucesso. accountId={}, tenantSchema={}, currentUsers={}, currentProducts={}, currentStorageMb={}, measuredAt={}",
                accountId,
                normalizedTenantSchema,
                measurement.currentUsers(),
                measurement.currentProducts(),
                0L,
                measuredAt
        );
    }

    /**
     * Valida entradas obrigatórias.
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