package brito.com.multitenancy001.infrastructure.publicschema.audit;

import brito.com.multitenancy001.infrastructure.tx.AfterTransactionCompletion;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Dispatcher para executar auditoria no schema PUBLIC sem violar as fronteiras transacionais do TENANT.
 *
 * <p><b>Solução:</b></p>
 * <ul>
 *   <li>Se houver transação atual (sync ativa): agenda para AFTER COMPLETION e executa em outro thread.</li>
 *   <li>Se não houver transação: executa imediatamente, abrindo um {@code REQUIRES_NEW} no PUBLIC.</li>
 * </ul>
 *
 * <p>Assim, eliminamos de vez o "UoW aninhado" TENANT -> PUBLIC.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PublicAuditDispatcher {

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final AfterTransactionCompletion afterTransactionCompletion;

    public void dispatch(Runnable fn) {
        if (fn == null) return;

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            afterTransactionCompletion.runAfterCompletion(() -> runPublicRequiresNew(fn, "afterCompletion"));
            return;
        }

        runPublicRequiresNew(fn, "no-sync");
    }

    private void runPublicRequiresNew(Runnable fn, String where) {
        try {
            publicSchemaUnitOfWork.requiresNew(() -> {
                fn.run();
                return null;
            });
        } catch (Exception e) {
            log.warn("⚠️ Falha ao executar auditoria PUBLIC (best-effort) | where={} | msg={}", where, e.getMessage(), e);
        }
    }
}