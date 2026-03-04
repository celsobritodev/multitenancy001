package brito.com.multitenancy001.infrastructure.publicschema.audit;

import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

/**
 * Serviço de auditoria SOC2-like (append-only) persistido no schema PUBLIC.
 *
 * <p><b>Problema real que este service resolve:</b></p>
 * <ul>
 *   <li>Em fluxo multi-tenant, é comum estar dentro de uma TX (TENANT ou PUBLIC) e precisar gravar auditoria PUBLIC.</li>
 *   <li>Se você tentar abrir uma nova TX PUBLIC no <b>mesmo thread</b> enquanto o Spring ainda está em commit/cleanup,
 *       você pode receber: <pre>IllegalTransactionStateException: Pre-bound JDBC Connection found!</pre></li>
 * </ul>
 *
 * <p><b>Regra de ouro adotada aqui:</b></p>
 * <ul>
 *   <li>Quando houver <b>TransactionSynchronization ativa</b>, preferimos <b>publicar evento</b> e gravar no listener
 *       em {@code AFTER_COMPLETION + @Async}, evitando o thread do commit/cleanup.</li>
 *   <li>Quando <b>não</b> houver sync:
 *     <ul>
 *       <li>Se não houver TX/resources: grava NOW em {@code REQUIRES_NEW} no PUBLIC.</li>
 *       <li>Se houver TX/resources: despacha async (fora do thread) e grava em {@code REQUIRES_NEW} no PUBLIC.</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p><b>Importante:</b> este serviço é <i>best-effort</i> (nunca quebra o fluxo principal). Porém, ele <b>nunca "pula"</b>
 * deliberadamente a auditoria: sempre tenta publicar/gravar imediatamente ou assíncrono.</p>
 */
@Service
@RequiredArgsConstructor
public class SecurityAuditService {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditService.class);

    private final SecurityAuditEventPublisher publisher;
    private final SecurityAuditTxWriter txWriter;
    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final AppClock appClock;

    /**
     * Executor dedicado para rodar tarefas após completion, fora do thread transacional.
     * <p>Deve ser um pool pequeno e estável (não usar executor "infinito").</p>
     */
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

        final boolean syncActive = TransactionSynchronizationManager.isSynchronizationActive();
        final boolean txActive = TransactionSynchronizationManager.isActualTransactionActive();

        final Map<Object, Object> resources = TransactionSynchronizationManager.getResourceMap();
        final boolean hasResources = resources != null && !resources.isEmpty();

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

        // =========================================================
        // 1) Caso ideal: há sync => publica evento para listener AFTER_COMPLETION + @Async
        // =========================================================
        if (syncActive) {
            try {
                publisher.publish(event);
                log.debug("✅ SecurityAudit publicado (syncActive) | actionType={} outcome={} accountId={} tenantSchema={}",
                        actionType, outcome, accountId, tenantSchema);
            } catch (Exception ex) {
                // Se publicar falhar, tentamos fallback de gravação direta.
                log.warn("⚠️ Falha ao publicar evento SecurityAudit; fallback para writeNow | actionType={} outcome={} accountId={} tenantSchema={} msg={}",
                        actionType, outcome, accountId, tenantSchema, ex.getMessage(), ex);
                safeWriteNow(event, "sync/publish-failed");
            }
            return;
        }

        // =========================================================
        // 2) Sem sync, mas também sem TX/resources => pode gravar NOW com segurança (PUBLIC requiresNew)
        // =========================================================
        if (!txActive && !hasResources) {
            safeWriteNow(event, "no-sync/no-tx");
            return;
        }

        // =========================================================
        // 3) Sem sync, mas há TX/resources => NUNCA gravar NOW no mesmo thread. Despacha async.
        // =========================================================
        log.warn("⚠️ SecurityAudit sem sync mas com contexto transacional; despachando async | actionType={} outcome={} accountId={} tenantSchema={} txActive={} hasResources={}",
                actionType, outcome, accountId, tenantSchema, txActive, hasResources);

        try {
            afterTxCompletionExecutor.execute(() -> safeWriteNow(event, "no-sync/async"));
        } catch (Exception ex) {
            // Último recurso: ainda tenta gravar no thread atual (best-effort),
            // mas com log claro. Idealmente isso não deve acontecer se o executor estiver ok.
            log.warn("⚠️ Falha ao despachar async; fallback para writeNow direto | actionType={} outcome={} accountId={} tenantSchema={} msg={}",
                    actionType, outcome, accountId, tenantSchema, ex.getMessage(), ex);
            safeWriteNow(event, "no-sync/fallback-direct");
        }
    }

    /**
     * Grava o evento imediatamente em uma TX PUBLIC REQUIRES_NEW.
     *
     * <p><b>Por que usar requiresNew?</b></p>
     * <ul>
     *   <li>Isola a auditoria do fluxo principal (commit/rollback não interferem).</li>
     *   <li>Evita acoplamento a TX chamadora e reduz risco de nesting indevido.</li>
     * </ul>
     */
    private void safeWriteNow(SecurityAuditRequestedEvent event, String where) {
        if (event == null) return;

        try {
            publicSchemaUnitOfWork.requiresNew(() -> {
                txWriter.write(event);
                return null;
            });

            log.debug("✅ SecurityAudit gravado | where={} | actionType={} outcome={} accountId={} tenantSchema={}",
                    where, event.actionType(), event.outcome(), event.accountId(), event.tenantSchema());

        } catch (Exception ex) {
            log.warn("⚠️ Falha ao gravar SecurityAudit (best-effort) | where={} | actionType={} outcome={} accountId={} tenantSchema={} msg={}",
                    where,
                    event.actionType(),
                    event.outcome(),
                    event.accountId(),
                    event.tenantSchema(),
                    ex.getMessage(),
                    ex);
        }
    }
}