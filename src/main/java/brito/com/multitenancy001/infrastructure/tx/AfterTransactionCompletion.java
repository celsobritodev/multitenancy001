package brito.com.multitenancy001.infrastructure.tx;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.CompletableFuture;

/**
 * Executor para agendar execução após o término da transação atual (commit OU rollback),
 * garantindo que o código rode fora do thread de commit/cleanup do Spring.
 *
 * <p><b>Por que isto existe?</b></p>
 * <ul>
 *   <li>Em ambientes com JPA + JDBC (mesmo DataSource), é comum existir {@code ConnectionHolder}
 *       e/ou {@code EntityManagerHolder} "pre-bound" no thread durante commit/cleanup.</li>
 *   <li>Se você tentar iniciar outra transação (mesmo JPA) nesse mesmo thread, pode estourar:
 *       <pre>IllegalTransactionStateException: Pre-bound JDBC Connection found!</pre></li>
 *   <li>Para auditoria SOC2-like, precisamos registrar também tentativas e falhas (rollback incluso),
 *       então usamos AFTER COMPLETION (não só AFTER COMMIT).</li>
 * </ul>
 *
 * <p><b>Regra de ouro deste componente:</b> a execução real é SEMPRE despachada para outro thread
 * via {@link TaskExecutor}, independentemente de haver sincronização ativa ou não.</p>
 */
@Slf4j
@Component
public class AfterTransactionCompletion {

    /**
     * Executor dedicado para operações "after completion".
     *
     * <p>Deve ser um executor leve e confiável; evite usar o mesmo pool de workloads pesados.</p>
     */
    private final TaskExecutor afterTxCompletionExecutor;

    public AfterTransactionCompletion(TaskExecutor afterTxCompletionExecutor) {
        this.afterTxCompletionExecutor = afterTxCompletionExecutor;
    }

    /**
     * Agenda {@code task} para executar após a transação atual finalizar (commit ou rollback).
     *
     * <p><b>CORREÇÃO:</b> Mesmo sem transação gerenciada (sem synchronization), 
     * nunca executa imediatamente no mesmo thread. Sempre despacha para async.</p>
     *
     * @param task tarefa a executar
     */
    public void runAfterCompletion(@Nullable Runnable task) {
        if (task == null) return;

        // ✅ CORREÇÃO: SEMPRE despachar async, nunca executar no mesmo thread
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            log.debug("Sem sync ativa - despachando async imediatamente");
            dispatchAsync("no-sync", task);
            return;
        }

        try {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    dispatchAsync("afterCompletion(status=" + status + ")", task);
                }
            });
        } catch (Exception e) {
            // NUNCA execute "imediato" aqui: pode estar no pior momento do lifecycle transacional.
            log.warn("⚠️ Falha ao registrar afterCompletion; despachando async (fallback) | msg={}", e.getMessage(), e);
            dispatchAsync("fallback-async", task);
        }
    }

    /**
     * Agenda {@code task} para executar após a transação atual finalizar (commit ou rollback),
     * recebendo o {@code status} de completion do Spring.
     *
     * @param task tarefa a executar com status
     */
    public void runAfterCompletionStatus(@Nullable TransactionCompletionTask task) {
        if (task == null) return;

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            dispatchAsync("no-sync", () -> task.run(TransactionSynchronization.STATUS_UNKNOWN));
            return;
        }

        try {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    dispatchAsync("afterCompletion(status=" + status + ")", () -> task.run(status));
                }
            });
        } catch (Exception e) {
            log.warn("⚠️ Falha ao registrar afterCompletion(status); despachando async (fallback) | msg={}", e.getMessage(), e);
            dispatchAsync("fallback-async", () -> task.run(TransactionSynchronization.STATUS_UNKNOWN));
        }
    }

    /**
     * Contrato para receber {@code status} de completion.
     */
    @FunctionalInterface
    public interface TransactionCompletionTask {
        void run(int status);
    }

    private void dispatchAsync(String where, Runnable task) {
        try {
            afterTxCompletionExecutor.execute(() -> safeRun(where + "/executor", task));
        } catch (Exception ex) {
            // Último fallback: garante que não executa no thread corrente.
            CompletableFuture.runAsync(() -> safeRun(where + "/fallback-completable", task));
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