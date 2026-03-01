package brito.com.multitenancy001.infrastructure.tx;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Executa uma ação somente APÓS o COMMIT da transação atual.
 *
 * Regra do projeto:
 * - Evita rodar PUBLIC (audit/login_identities) dentro do mesmo thread/tx TENANT.
 * - Evita "Pre-bound JDBC Connection found!" por mistura de managers.
 */
@Slf4j
@Component
public class AfterTransactionCommit {

    public void runAfterCommit(Runnable action) {
        if (action == null) return;

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // Sem TX sincronizada: roda agora (best-effort)
            safeRun(action);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                safeRun(action);
            }
        });
    }

    private static void safeRun(Runnable action) {
        try {
            action.run();
        } catch (Exception e) {
            log.warn("⚠️ AfterTransactionCommit action failed (best-effort) | msg={}", e.getMessage(), e);
        }
    }
}