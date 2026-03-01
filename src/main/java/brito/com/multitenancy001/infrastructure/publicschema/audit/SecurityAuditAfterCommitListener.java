// src/main/java/brito/com/multitenancy001/infrastructure/publicschema/audit/SecurityAuditAfterCommitListener.java
package brito.com.multitenancy001.infrastructure.publicschema.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Consome o evento de auditoria APENAS depois do COMMIT da transacao principal.
 *
 * <p>Isso evita:</p>
 * <ul>
 *   <li>Mix de PUBLIC JPA dentro de TX TENANT</li>
 *   <li>Erro: Pre-bound JDBC Connection found!</li>
 * </ul>
 *
 * <p>Best-effort: falha aqui nao deve quebrar request principal (que ja commitou).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityAuditAfterCommitListener {

    private final SecurityAuditTxWriter txWriter;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAfterCommit(SecurityAuditRequestedEvent event) {
        try {
            txWriter.write(event);
        } catch (Exception ex) {
            log.warn("SecurityAudit AFTER_COMMIT falhou (best-effort). actionType={}, accountId={}, tenantSchema={}, msg={}",
                    event.actionType(), event.accountId(), event.tenantSchema(), ex.getMessage(), ex);
        }
    }
}