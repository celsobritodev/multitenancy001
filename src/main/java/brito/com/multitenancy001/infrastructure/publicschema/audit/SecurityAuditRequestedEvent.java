// src/main/java/brito/com/multitenancy001/infrastructure/publicschema/audit/SecurityAuditRequestedEvent.java
package brito.com.multitenancy001.infrastructure.publicschema.audit;

import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;

import java.time.Instant;

/**
 * Evento publicado durante a transacao principal (TENANT ou PUBLIC) para que a gravacao
 * de auditoria ocorra APENAS AFTER_COMMIT, evitando misturar PUBLIC JPA dentro de TX ativa.
 *
 * <p>Regra do projeto:</p>
 * <ul>
 *   <li>Nao iniciar transacao PUBLIC JPA "em cima" de outra transacao ativa no mesmo thread.</li>
 *   <li>Auditoria e append-only e best-effort: nao pode quebrar fluxo principal.</li>
 * </ul>
 */
public record SecurityAuditRequestedEvent(
        Instant occurredAt,
        Long accountId,
        String tenantSchema,
        SecurityAuditActionType actionType,
        AuditOutcome outcome,
        String actorEmail,
        Long actorUserId,
        String targetEmail,
        Long targetUserId,
        String detailsJson
) {
}