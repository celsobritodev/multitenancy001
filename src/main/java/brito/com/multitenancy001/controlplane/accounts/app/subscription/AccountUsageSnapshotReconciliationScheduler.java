package brito.com.multitenancy001.controlplane.accounts.app.subscription;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Scheduler independente para reconciliação periódica dos usage snapshots.
 *
 * <p>Não substitui o scheduler existente do Control Plane; atua como job
 * específico de consistência eventual para subscription usage.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountUsageSnapshotReconciliationScheduler {

    private final AccountUsageSnapshotReconciliationService accountUsageSnapshotReconciliationService;

    /**
     * Executa reconciliação periódica.
     *
     * <p>Default: a cada 5 minutos. Ajustável por propriedade.</p>
     */
    @Scheduled(fixedDelayString = "${app.subscription.usage-reconciliation-delay-ms:300000}")
    public void reconcileUsageSnapshots() {
        log.info("Disparando scheduler de reconciliação de usage snapshots.");

        accountUsageSnapshotReconciliationService.reconcileAll();

        log.info("Scheduler de reconciliação de usage snapshots concluído.");
    }
}