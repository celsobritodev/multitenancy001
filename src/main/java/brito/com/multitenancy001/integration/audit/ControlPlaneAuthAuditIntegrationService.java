package brito.com.multitenancy001.integration.audit;

import brito.com.multitenancy001.infrastructure.publicschema.audit.AuthEventAuditService;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.AuthDomain;
import brito.com.multitenancy001.shared.domain.audit.AuthEventType;
import brito.com.multitenancy001.shared.json.JsonDetailsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Integração ControlPlane -> Infra (auditoria de autenticação).
 *
 * Regras:
 * - controlplane.* NÃO importa infrastructure.publicschema.audit.* (aqui é integration).
 * - detailsJson SEMPRE via JsonDetailsMapper (nunca escapeJson manual).
 */
@Service
@RequiredArgsConstructor
public class ControlPlaneAuthAuditIntegrationService {

    private final AuthEventAuditService authEventAuditService;
    private final JsonDetailsMapper jsonDetailsMapper;

    public void auditLoginSuccess(Long userId, String email) {
        /** comentário: registra sucesso de login do Control Plane */
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
        /** comentário: registra falha de login do Control Plane com reason estruturado */
        String detailsJson = (reason == null) ? null : toJson(m("reason", reason));

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

    private String toJson(Object details) {
        /** comentário: converte details (Map/record/String) em JSON string compatível com jsonb */
        if (details == null) return null;
        return jsonDetailsMapper.toJsonNode(details).toString();
    }

    private static Map<String, Object> m(Object... kv) {
        /** comentário: cria LinkedHashMap em pares key/value com ordem estável */
        Map<String, Object> m = new LinkedHashMap<>();
        if (kv == null) return m;
        if (kv.length % 2 != 0) throw new IllegalArgumentException("m(kv): quantidade ímpar de argumentos");
        for (int i = 0; i < kv.length; i += 2) {
            Object k = kv[i];
            Object v = kv[i + 1];
            if (k == null) continue;
            m.put(String.valueOf(k), v);
        }
        return m;
    }
}