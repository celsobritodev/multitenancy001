package brito.com.multitenancy001.integration.tenant.subscription;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fachada fina de integração entre outros contextos e o contexto Tenant
 * para sincronização de uso de subscription/quota.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Validar entradas do fluxo cross-boundary.</li>
 *   <li>Delegar a sincronização do snapshot público ao service técnico especializado.</li>
 *   <li>Expor uma API segura, sem permitir medição direta para regra de negócio.</li>
 * </ul>
 *
 * <p>Regras arquiteturais:</p>
 * <ul>
 *   <li>O Control Plane não deve medir uso diretamente no tenant.</li>
 *   <li>A fonte de verdade consumida pelo Control Plane é o snapshot público materializado.</li>
 *   <li>A medição real permanece encapsulada no fluxo técnico interno de sincronização.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantSubscriptionUsageIntegrationService {

    private final TenantUsageSnapshotSyncService tenantUsageSnapshotSyncService;

    /**
     * Sincroniza o snapshot público de uso da conta a partir do tenant.
     *
     * @param tenantSchema schema do tenant
     * @param accountId id da conta
     */
    public void syncPublicUsageSnapshot(String tenantSchema, Long accountId) {
        validateInputs(tenantSchema, accountId);

        String normalizedTenantSchema = normalizeTenantSchema(tenantSchema);

        log.info(
                "Delegando sincronização de usage snapshot via integração tenant. accountId={}, tenantSchema={}",
                accountId,
                normalizedTenantSchema
        );

        tenantUsageSnapshotSyncService.syncUsageSnapshot(normalizedTenantSchema, accountId);

        log.info(
                "Sincronização de usage snapshot via integração tenant concluída com sucesso. accountId={}, tenantSchema={}",
                accountId,
                normalizedTenantSchema
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