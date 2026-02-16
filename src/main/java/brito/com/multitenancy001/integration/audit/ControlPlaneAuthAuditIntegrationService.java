package brito.com.multitenancy001.integration.audit;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.infrastructure.publicschema.audit.AuthEventAuditService;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.AuthDomain;
import brito.com.multitenancy001.shared.domain.audit.AuthEventType;
import lombok.RequiredArgsConstructor;

/**
 * Integração ControlPlane -> Infra (auditoria de autenticação).
 *
 * Regra: controlplane.* NÃO importa infrastructure.publicschema.audit.*
 */
@Service
@RequiredArgsConstructor
public class ControlPlaneAuthAuditIntegrationService {

    private final AuthEventAuditService authEventAuditService;

    public void auditLoginSuccess(Long userId, String email) {
        authEventAuditService.record(
                AuthDomain.CONTROLPLANE,
                AuthEventType.LOGIN_SUCCESS,
                AuditOutcome.SUCCESS,
                email,
                userId,
                null,
                null,
                null
        );
    }

    public void auditLoginFailure(String email, String reason) {
        String detailsJson = reason == null ? null : "{\"reason\":\"" + escapeJson(reason) + "\"}";

        authEventAuditService.record(
                AuthDomain.CONTROLPLANE,
                AuthEventType.LOGIN_FAILURE,
                AuditOutcome.FAILURE,
                email,
                null,
                null,
                null,
                detailsJson
        );
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
