package brito.com.multitenancy001.infrastructure.publicschema.audit;

import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

/**
 * Serviço de auditoria SOC2-like (append-only) persistido no schema PUBLIC.
 *
 * <p><b>REGRAS SIMPLIFICADAS:</b></p>
 * <ul>
 *   <li>✅ SEMPRE executa em thread separada (afterTxCompletionExecutor)</li>
 *   <li>✅ SEMPRE usa REQUIRES_NOVO no PUBLIC</li>
 *   <li>✅ NUNCA executa no thread da requisição</li>
 *   <li>✅ Best-effort: nunca quebra o fluxo principal</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityAuditService {

    private final SecurityAuditTxWriter txWriter;
    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final AppClock appClock;

    @Qualifier("afterTxCompletionExecutor")
    private final TaskExecutor afterTxCompletionExecutor;

    /**
     * Registra um evento de auditoria.
     *
     * <p>Este método <b>não</b> lança exceção (best-effort). Caso falhe, apenas loga ⚠️.</p>
     */
    public void record(
            SecurityAuditActionType actionType,
            AuditOutcome outcome,
            String actorEmail,
            Long actorUserId,
            String targetEmail,
            Long targetUserId,
            Long accountId,
            String tenantSchema,
            String detailsJson
    ) {
        final var now = appClock.instant();

        final SecurityAuditRequestedEvent event = new SecurityAuditRequestedEvent(
                now,
                accountId,
                tenantSchema,
                actionType,
                outcome,
                actorEmail,
                actorUserId,
                targetEmail,
                targetUserId,
                detailsJson
        );

        // ✅ REGRA SIMPLES: SEMPRE executa em thread separada
        try {
            afterTxCompletionExecutor.execute(() -> {
                try {
                    publicSchemaUnitOfWork.requiresNew(() -> {
                        txWriter.write(event);
                        return null;
                    });
                    log.debug("✅ SecurityAudit gravado | actionType={} outcome={} accountId={} tenantSchema={}",
                            actionType, outcome, accountId, tenantSchema);
                } catch (Exception ex) {
                    log.warn("⚠️ Falha ao gravar SecurityAudit (best-effort) | actionType={} outcome={} accountId={} tenantSchema={} msg={}",
                            actionType, outcome, accountId, tenantSchema, ex.getMessage(), ex);
                }
            });
        } catch (Exception ex) {
            log.warn("⚠️ Falha ao agendar SecurityAudit | actionType={} outcome={} accountId={} tenantSchema={} msg={}",
                    actionType, outcome, accountId, tenantSchema, ex.getMessage(), ex);
        }
    }
}