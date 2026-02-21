package brito.com.multitenancy001.controlplane.scheduling.infra;

import brito.com.multitenancy001.controlplane.scheduling.domain.AccountJobSchedule;
import brito.com.multitenancy001.controlplane.scheduling.persistence.AccountJobScheduleRepository;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Scheduler de jobs do Control Plane.
 *
 * Regras:
 * - Nunca pode derrubar a aplicação.
 * - Se schema não estiver pronto, loga e retorna.
 * - Infra NÃO bloqueia Newman / E2E.
 * - AppClock é a única fonte de tempo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccountJobScheduler {

    private final AccountJobScheduleRepository repository;
    private final AppClock appClock;

    /**
     * Executa jobs vencidos.
     *
     * IMPORTANTE:
     * - Fail-soft: se tabela não existir ou DB não estiver pronto, apenas loga e sai.
     */
    @Scheduled(fixedDelayString = "${app.jobs.scan-delay-ms:60000}")
    public void runDueJobs() {
        /* Busca jobs "due" usando AppClock e não deixa infra travar o E2E. */
        try {
            Instant now = appClock.instant();

            List<AccountJobSchedule> due = repository.findDue(now);

            // Execução real do job (se existir) ficaria aqui.
            // Por enquanto, apenas log/debug para não mudar semântica do projeto.
            if (!due.isEmpty()) {
                log.debug("AccountJobScheduler: {} job(s) due (now={})", due.size(), now);
            }

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