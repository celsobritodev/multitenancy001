package brito.com.multitenancy001.integration.audit;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.infrastructure.publicschema.audit.SecurityAuditService;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.SecurityAuditActionType;
import lombok.RequiredArgsConstructor;

/**
 * Integração: ControlPlane -> Infrastructure (SecurityAuditService).
 */
@Service
@RequiredArgsConstructor
public class ControlPlaneSecurityAuditIntegrationService {

    private final SecurityAuditService securityAuditService;

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
        securityAuditService.record(
                actionType,
                outcome,
                actorEmail,
                actorUserId,
                targetEmail,
                targetUserId,
                accountId,
                tenantSchema,
                detailsJson
        );
    }
}
