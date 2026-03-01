package brito.com.multitenancy001.infrastructure.tx;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.CompletableFuture;

/**
 * Executor para agendar execução após o término da transação atual.
 *
 * <p>Por que AFTER COMPLETION e não apenas AFTER COMMIT?</p>
 * <ul>
 *   <li>Auditoria SOC2-like exige registrar também FAILURE/DENIED (rollback incluso).</li>
 *   <li>Evita "Pre-bound JDBC Connection found" ao garantir que o runnable execute
 *       fora da janela da TX atual (commit ou rollback já concluído).</li>
 * </ul>
 *
 * <p>Comportamento:</p>
 * <ul>
 *   <li>Se NÃO houver synchronization ativa: executa imediatamente.</li>
 *   <li>Se houver synchronization ativa: registra callback e executa após completion.</li>
 *   <li>Se registerSynchronization falhar: executa ASYNC (nunca imediato no mesmo thread).</li>
 * </ul>
 */
@Slf4j
@Component
public class AfterTransactionCompletion {

    /**
     * Agenda {@code task} para executar após a transação atual finalizar (commit ou rollback).
     *
     * @param task tarefa a executar
     */
    public void runAfterCompletion(Runnable task) {
        if (task == null) return;

        // Gatilho correto: se dá pra registrar afterCompletion
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            safeRun("no-sync", task);
            return;
        }

        try {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    safeRun("afterCompletion(status=" + status + ")", task);
                }
            });
        } catch (Exception e) {
            // NUNCA execute imediato aqui: pode estar com resources pre-bound no thread atual.
            log.warn("⚠️ Falha ao registrar afterCompletion; executando async | msg={}", e.getMessage(), e);
            CompletableFuture.runAsync(() -> safeRun("fallback-async", task));
        }
    }

    private void safeRun(String where, Runnable task) {
        try {
            task.run();
        } catch (Exception e) {
            log.warn("⚠️ Tarefa afterCompletion falhou (best-effort) | where={} | msg={}", where, e.getMessage(), e);
        }
    }
}