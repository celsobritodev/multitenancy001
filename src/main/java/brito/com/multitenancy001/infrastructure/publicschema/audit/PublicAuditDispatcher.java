package brito.com.multitenancy001.infrastructure.publicschema.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import brito.com.multitenancy001.infrastructure.persistence.TxExecutor;

/**
 * Dispatcher de auditoria PUBLIC que evita nesting ilegal de transações
 * (TENANT JPA -> PUBLIC JDBC/JPA) no mesmo thread.
 *
 * <p>Problema que resolve:</p>
 * <ul>
 *   <li>Erro: "Pre-bound JDBC Connection found! JpaTransactionManager does not support running within DataSourceTransactionManager..."</li>
 * </ul>
 *
 * <p>Estratégia:</p>
 * <ul>
 *   <li>Se NÃO há transação ativa no thread: grava imediatamente em PUBLIC (REQUIRES_NEW).</li>
 *   <li>Se HÁ transação ativa (ex: TENANT): agenda a gravação para <b>afterCompletion</b>
 *       (commit ou rollback), quando os resources já foram liberados.</li>
 * </ul>
 *
 * <p>Observação:</p>
 * <ul>
 *   <li>afterCompletion é usado para registrar auditoria tanto de sucesso quanto de falha.</li>
 *   <li>Falhas ao gravar audit não devem derrubar a request de negócio (best-effort), mas são logadas.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PublicAuditDispatcher {

    private final TxExecutor txExecutor;

    /**
     * Executa uma ação de auditoria no schema PUBLIC de forma segura, evitando nesting de transaction managers.
     *
     * @param action ação que grava audit (ex: chamar SecurityAuditService.record(...))
     */
    public void dispatch(Runnable action) {
        if (action == null) throw new IllegalArgumentException("action é obrigatório");

        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            // Sem tx ativa: grava agora
            runInPublicRequiresNew(action);
            return;
        }

        // Há tx ativa (ex: tenant): agenda para depois do commit/rollback
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                try {
                    runInPublicRequiresNew(action);
                } catch (Exception e) {
                    log.warn("⚠️ Falha ao gravar auditoria PUBLIC em afterCompletion: {}", e.getMessage(), e);
                }
            }
        });
    }

    private void runInPublicRequiresNew(Runnable action) {
        txExecutor.inPublicRequiresNew(() -> {
            action.run();
            return null;
        });
    }
}