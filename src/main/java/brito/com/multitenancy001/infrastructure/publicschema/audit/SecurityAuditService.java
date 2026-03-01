// src/main/java/brito/com/multitenancy001/infrastructure/publicschema/audit/SecurityAuditService.java
package brito.com.multitenancy001.infrastructure.publicschema.audit;

import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class SecurityAuditService {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditService.class);

    private final SecurityAuditEventPublisher publisher;
    private final SecurityAuditTxWriter txWriter; // fallback quando nao ha TX
    private final AppClock appClock;

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

        // 1) Caso ideal: consegue registrar AFTER_COMMIT
        if (syncActive) {
            try {
                publisher.publish(
                        now, accountId, tenantSchema, actionType, outcome,
                        actorEmail, actorUserId, targetEmail, targetUserId, detailsJson
                );
            } catch (Exception ex) {
                log.warn("⚠️ Falha ao publicar evento SecurityAudit (best-effort) | actionType={} outcome={} accountId={} tenantSchema={} msg={}",
                        actionType, outcome, accountId, tenantSchema, ex.getMessage(), ex);
            }
            return;
        }

        // 2) Nao tem sync, mas ja tem contexto/transacao/resources no thread:
        // NUNCA tente NOW aqui, porque e exatamente o cenario do "Pre-bound JDBC Connection found!"
        if (txActive || hasResources) {
            log.warn("⚠️ SecurityAudit pulado (best-effort) por contexto transacional sem synchronization | actionType={} outcome={} accountId={} tenantSchema={} txActive={} hasResources={}",
                    actionType, outcome, accountId, tenantSchema, txActive, hasResources);
            return;
        }

        // 3) Sem TX/sync/resources: pode gravar NOW com seguranca (isolado)
        try {
            txWriter.write(new SecurityAuditRequestedEvent(
                    now, accountId, tenantSchema, actionType, outcome,
                    actorEmail, actorUserId, targetEmail, targetUserId, detailsJson
            ));
        } catch (Exception ex) {
            log.warn("⚠️ Falha ao gravar SecurityAudit NOW (best-effort) | actionType={} outcome={} accountId={} tenantSchema={} msg={}",
                    actionType, outcome, accountId, tenantSchema, ex.getMessage(), ex);
        }
    }
}