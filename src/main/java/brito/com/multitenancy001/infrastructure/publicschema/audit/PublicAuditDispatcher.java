package brito.com.multitenancy001.infrastructure.publicschema.audit;

import brito.com.multitenancy001.infrastructure.tx.AfterTransactionCompletion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Dispatcher de ações que precisam ocorrer no PUBLIC schema com segurança.
 *
 * <p>Regra do projeto:</p>
 * <ul>
 *   <li>NUNCA rodar PUBLIC "dentro" de uma transação TENANT no mesmo thread.</li>
 *   <li>Para evitar "Pre-bound JDBC Connection found", este dispatcher agenda a execução
 *       para ocorrer somente após a transação atual finalizar (commit OU rollback).</li>
 * </ul>
 *
 * <p>Isso garante:</p>
 * <ul>
 *   <li>Sem nesting TENANT->PUBLIC no mesmo thread</li>
 *   <li>Auditoria ainda registra SUCCESS/FAIL/DENIED (pois roda afterCompletion)</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PublicAuditDispatcher {

    private final AfterTransactionCompletion afterTransactionCompletion;

    /**
     * Executa a ação com garantia de NÃO conflitar com transações TENANT/JPA ativas no thread.
     *
     * <p>Se houver transação ativa, agenda para afterCompletion.</p>
     *
     * @param action ação best-effort
     */
    public void dispatch(Runnable action) {
        if (action == null) return;

        afterTransactionCompletion.runAfterCompletion(() -> {
            try {
                action.run();
            } catch (Exception e) {
                log.warn("⚠️ PublicAuditDispatcher action failed (best-effort) | msg={}", e.getMessage(), e);
            }
        });
    }
}