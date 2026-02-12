package brito.com.multitenancy001.infrastructure.publicschema.audit;

import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.AuthDomain;
import brito.com.multitenancy001.shared.domain.audit.AuthEventType;
import brito.com.multitenancy001.tenant.auth.app.audit.TenantAuthAuditRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TenantAuthAuditRecorderPublicSchemaJpa implements TenantAuthAuditRecorder {

    private final AuthEventAuditService authEventAuditService;

    @Override
    public void record(AuthDomain domain,
                       AuthEventType type,
                       AuditOutcome outcome,
                       String email,
                       Long userId,
                       Long accountId,
                       String tenantSchema,
                       String detailsJson) {

        authEventAuditService.record(domain, type, outcome, email, userId, accountId, tenantSchema, detailsJson);
    }
}
