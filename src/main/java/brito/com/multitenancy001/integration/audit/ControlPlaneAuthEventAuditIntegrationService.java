package brito.com.multitenancy001.integration.audit;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.infrastructure.publicschema.audit.AuthEventAuditService;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.AuthDomain;
import brito.com.multitenancy001.shared.domain.audit.AuthEventType;
import lombok.RequiredArgsConstructor;

/**
 * Integração: ControlPlane -> Infrastructure (AuthEventAuditService).
 */
@Service
@RequiredArgsConstructor
public class ControlPlaneAuthEventAuditIntegrationService {

    private final AuthEventAuditService authEventAuditService;

    /**
     * Wrapper compatível com o AuthEventAuditService atual:
     * record(AuthDomain, AuthEventType, AuditOutcome, principalEmail, principalUserId, accountId, tenantSchema, detailsJson)
     */
    public void record(
            AuthDomain domain,
            AuthEventType eventType,
            AuditOutcome outcome,
            String subjectEmail,
            String actorEmail,
            Long actorUserId,
            String tenantSchema,
            String detailsJson
    ) {
        // subjectEmail é o "principalEmail" persistido.
        // actorUserId (quando existir) é o "principalUserId".
        // accountId: este wrapper antigo não recebia; fica null por enquanto.
        authEventAuditService.record(
                domain,
                eventType,
                outcome,
                subjectEmail,
                actorUserId,
                null,
                tenantSchema,
                detailsJson
        );
    }
}
