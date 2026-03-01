// src/main/java/brito/com/multitenancy001/infrastructure/publicschema/audit/SecurityAuditEventPublisher.java
package brito.com.multitenancy001.infrastructure.publicschema.audit;

import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Publica eventos de auditoria para serem persistidos AFTER_COMMIT.
 *
 * <p>Regra:</p>
 * <ul>
 *   <li>NAO grava audit aqui. Apenas publica o evento.</li>
 *   <li>Persistencia real ocorre em {@link SecurityAuditAfterCommitListener}.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class SecurityAuditEventPublisher {

    private final ApplicationEventPublisher publisher;

    public void publish(
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
        publisher.publishEvent(new SecurityAuditRequestedEvent(
                occurredAt,
                accountId,
                tenantSchema,
                actionType,
                outcome,
                actorEmail,
                actorUserId,
                targetEmail,
                targetUserId,
                detailsJson
        ));
    }
}