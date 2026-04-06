package brito.com.multitenancy001.controlplane.accounts.app.subscription;

import java.util.List;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.persistence.AccountUsageSyncQueryRepository;
import brito.com.multitenancy001.integration.tenant.subscription.TenantSubscriptionUsageIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de reconciliação periódica dos snapshots públicos de uso.
 *
 * <p>Objetivo:</p>
 * <ul>
 *   <li>Garantir consistência eventual entre tenant real e snapshot público.</li>
 *   <li>Recalcular snapshots mesmo quando algum write-path não disparar refresh after-commit.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountUsageSnapshotReconciliationService {

    private final AccountUsageSyncQueryRepository accountUsageSyncQueryRepository;
    private final TenantSubscriptionUsageIntegrationService tenantSubscriptionUsageIntegrationService;

    /**
     * Recalcula snapshots públicos de todas as contas elegíveis.
     */
    public void reconcileAll() {
        List<AccountUsageSyncTarget> targets = accountUsageSyncQueryRepository.findAllUsageSyncTargets();

        log.info("Iniciando reconciliação global de usage snapshots. totalTargets={}", targets.size());

        for (AccountUsageSyncTarget target : targets) {
            try {
                tenantSubscriptionUsageIntegrationService.syncPublicUsageSnapshot(
                        target.tenantSchema(),
                        target.accountId()
                );
            } catch (Exception ex) {
                log.error(
                        "Erro na reconciliação de usage snapshot. accountId={}, tenantSchema={}, exType={}, message={}",
                        target.accountId(),
                        target.tenantSchema(),
                        ex.getClass().getName(),
                        ex.getMessage(),
                        ex
                );
            }
        }

        log.info("Reconciliação global de usage snapshots concluída. totalTargets={}", targets.size());
    }
}