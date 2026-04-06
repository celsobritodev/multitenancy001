package brito.com.multitenancy001.tenant.subscription.app;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.infrastructure.persistence.tx.AfterCommit;
import brito.com.multitenancy001.integration.tenant.subscription.TenantSubscriptionUsageIntegrationService;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço responsável por agendar a sincronização do usage snapshot
 * somente após o commit bem-sucedido de operações tenant que alteram uso.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Centralizar o disparo after-commit para refresh de snapshot.</li>
 *   <li>Evitar repetição dessa mecânica em múltiplos write services tenant.</li>
 *   <li>Preservar o boundary: write tenant primeiro, snapshot público depois do commit.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantUsageSnapshotAfterCommitService {

    private final TenantSubscriptionUsageIntegrationService tenantSubscriptionUsageIntegrationService;

    /**
     * Agenda a sincronização do snapshot público para execução após commit.
     *
     * @param accountId id da conta
     * @param tenantSchema schema do tenant
     */
    public void scheduleRefreshAfterCommit(Long accountId, String tenantSchema) {
        validateInputs(accountId, tenantSchema);

        String normalizedTenantSchema = tenantSchema.trim();

        log.info(
                "Agendando refresh de usage snapshot após commit. accountId={}, tenantSchema={}",
                accountId,
                normalizedTenantSchema
        );

        AfterCommit.runAfterCommitRequired(() -> {
            log.info(
                    "Executando refresh de usage snapshot após commit. accountId={}, tenantSchema={}",
                    accountId,
                    normalizedTenantSchema
            );

            tenantSubscriptionUsageIntegrationService.syncPublicUsageSnapshot(
                    normalizedTenantSchema,
                    accountId
            );
        });
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
}