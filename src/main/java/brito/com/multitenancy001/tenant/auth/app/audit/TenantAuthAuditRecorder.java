package brito.com.multitenancy001.tenant.auth.app.audit;

import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.AuthDomain;
import brito.com.multitenancy001.shared.domain.audit.AuthEventType;

public interface TenantAuthAuditRecorder {

    void record(
            AuthDomain domain,
            AuthEventType type,
            AuditOutcome outcome,
            String email,
            Long userId,
            Long accountId,
            String tenantSchema,
            String detailsJson
    );
}
