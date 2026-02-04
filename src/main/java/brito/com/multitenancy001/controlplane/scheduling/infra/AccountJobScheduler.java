package brito.com.multitenancy001.controlplane.scheduling.infra;

import java.time.Instant;

import org.springframework.context.annotation.DependsOn;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import brito.com.multitenancy001.controlplane.scheduling.persistence.AccountJobScheduleRepository;
import brito.com.multitenancy001.shared.executor.PublicUnitOfWork;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Scheduler do Control Plane.
 *
 * ✅ DependsOn("flywayInitializer") garante que o Flyway do PUBLIC já rodou
 * antes deste bean existir/rodar (sem sleep/delay).
 */
@Component
@RequiredArgsConstructor
@Slf4j
@DependsOn("flywayInitializer")
public class AccountJobScheduler {

    private final AccountJobScheduleRepository repo;
    private final PublicUnitOfWork uow;

    @Scheduled(fixedDelayString = "PT30S")
    public void runDueJobs() {
        uow.requiresNew(() -> {
            var now = Instant.now();
            var due = repo.findDue(now);

            if (!due.isEmpty()) {
                log.info("⏱️ Encontrados {} jobs vencidos (now={})", due.size(), now);
            }

            // TODO: executar jobs
        });
    }
}
