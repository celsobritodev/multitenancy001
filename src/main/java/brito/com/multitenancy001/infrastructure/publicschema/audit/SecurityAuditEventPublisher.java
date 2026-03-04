package brito.com.multitenancy001.infrastructure.publicschema.audit;

import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Publica eventos de auditoria para serem persistidos AFTER_COMPLETION.
 *
 * <p><b>Regra:</b></p>
 * <ul>
 *   <li>Não persiste audit aqui. Apenas publica o evento.</li>
 *   <li>Persistência real ocorre em {@link SecurityAuditAfterCommitListener}
 *       (AFTER_COMPLETION + @Async) para evitar pre-bound no thread de commit/cleanup.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class SecurityAuditEventPublisher {

    private final ApplicationEventPublisher publisher;

    /**
     * Publica um evento pronto.
     *
     * @param event evento de auditoria (não nulo)
     */
    public void publish(SecurityAuditRequestedEvent event) {
        if (event == null) return;
        publisher.publishEvent(event);
    }

    /**
     * Publica um evento de auditoria montando o {@link SecurityAuditRequestedEvent}.
     */
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