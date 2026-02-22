package brito.com.multitenancy001.controlplane.scheduling.infra;

import brito.com.multitenancy001.controlplane.scheduling.persistence.AccountJobScheduleRepository;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler de jobs do Control Plane.
 *
 * Regras:
 * - Nunca pode derrubar a aplicação
 * - Se schema não estiver pronto, loga e retorna
 * - Infra NÃO bloqueia Newman / E2E
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountJobScheduler {

    private final AccountJobScheduleRepository accountJobScheduleRepository;
    private final AppClock appClock;

    /**
     * Executa jobs vencidos.
     *
     * IMPORTANTE:
     * - Fail-soft: se tabela não existir ou DB não estiver pronto,
     *   apenas loga e sai.
     */
    @Scheduled(fixedDelayString = "${app.jobs.scan-delay-ms:60000}")
    public void runDueJobs() {
        /* comentário: varre jobs vencidos e nunca derruba a aplicação */
        try {
            accountJobScheduleRepository.findDue(appClock.instant())
                    .forEach(job -> {
                        // execução real do job (se existir)
                    });
        } catch (DataAccessException ex) {
            log.warn(
                    "Scheduler ignorado (schema ainda não pronto ou migration ausente). Motivo: {}",
                    ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage()
            );
        } catch (Exception ex) {
            log.error("Erro inesperado no AccountJobScheduler", ex);
        }
    }
}