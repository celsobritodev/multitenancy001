// src/main/java/brito/com/multitenancy001/infrastructure/publicschema/audit/PublicAuditDispatcher.java
package brito.com.multitenancy001.infrastructure.publicschema.audit;

import brito.com.multitenancy001.infrastructure.tx.AfterTransactionCompletion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Dispatcher para acoes best-effort que precisam ocorrer apos a transacao atual finalizar.
 *
 * <p>Uso:</p>
 * <ul>
 *   <li>Se NAO houver TX ativa: executa imediatamente (best-effort).</li>
 *   <li>Se houver TX ativa: agenda para afterCompletion (commit ou rollback).</li>
 * </ul>
 *
 * <p>Obs:</p>
 * Para auditoria e login identities, prefira os servicos dedicados (SecurityAuditService / LoginIdentityProvisioningService),
 * pois eles ja implementam as regras do projeto. Este dispatcher fica como utilitario geral.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PublicAuditDispatcher {

    private final AfterTransactionCompletion afterTransactionCompletion;

    public void dispatch(Runnable action) {
        if (action == null) return;

        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            try {
                action.run();
            } catch (Exception e) {
                log.warn("⚠️ PublicAuditDispatcher action failed (best-effort) | msg={}", e.getMessage(), e);
            }
            return;
        }

        afterTransactionCompletion.runAfterCompletion(() -> {
            try {
                action.run();
            } catch (Exception e) {
                log.warn("⚠️ PublicAuditDispatcher action failed (best-effort) | msg={}", e.getMessage(), e);
            }
        });
    }
}