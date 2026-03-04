package brito.com.multitenancy001.infrastructure.publicschema.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listener do evento {@link SecurityAuditRequestedEvent}.
 *
 * <p><b>Por que AFTER_COMPLETION?</b></p>
 * <ul>
 *   <li>Auditoria SOC2-like precisa registrar também cenários de rollback (FAILURE/DENIED).</li>
 *   <li>AFTER_COMMIT perderia eventos de rollback.</li>
 * </ul>
 *
 * <p><b>Por que @Async?</b></p>
 * <ul>
 *   <li>Evita executar no mesmo thread do commit/cleanup do Spring.</li>
 *   <li>Isso elimina a classe de erro: <i>"Pre-bound JDBC Connection found!"</i>.</li>
 * </ul>
 *
 * <p><b>Contrato:</b> best-effort (não lança exceção).</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityAuditAfterCommitListener {

    private final SecurityAuditTxWriter txWriter;

    /**
     * Persiste a auditoria após o término da transação que originou o evento (commit ou rollback).
     *
     * @param event evento solicitado (pode ser null)
     */
    @Async("afterTxCompletionExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION)
    public void on(SecurityAuditRequestedEvent event) {
        if (event == null) return;

        try {
            txWriter.write(event);
            log.debug("✅ SecurityAudit listener persisted | actionType={} outcome={} accountId={} tenantSchema={}",
                    event.actionType(), event.outcome(), event.accountId(), event.tenantSchema());
        } catch (Exception e) {
            log.warn("⚠️ Falha ao gravar SecurityAudit no listener (best-effort) | actionType={} outcome={} accountId={} tenantSchema={} msg={}",
                    event.actionType(), event.outcome(), event.accountId(), event.tenantSchema(), e.getMessage(), e);
        }
    }
}