package brito.com.multitenancy001.tenant.auth.app;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.auth.app.AuthRefreshSessionService;
import brito.com.multitenancy001.shared.auth.app.dto.JwtResult;
import brito.com.multitenancy001.shared.auth.domain.AuthSessionDomain;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.AuthDomain;
import brito.com.multitenancy001.shared.domain.audit.AuthEventType;
import brito.com.multitenancy001.shared.json.JsonDetailsMapper;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.auth.app.audit.TenantAuthAuditRecorder;
import brito.com.multitenancy001.tenant.auth.app.boundary.TenantAuthMechanics;
import brito.com.multitenancy001.tenant.auth.app.boundary.TenantRefreshIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Serviço de refresh token do Tenant.
 *
 * Regras:
 * - refresh rotaciona o refresh token no servidor (logout forte depende disso).
 * - Auditoria SEMPRE com details estruturado (Map/record) serializado via JsonDetailsMapper.
 * - Nunca montar JSON na mão.
 *
 * Otimização:
 * - resolveRefreshIdentity não faz query; a query ocorre só dentro do refreshTenantJwt.
 */
@Service
@RequiredArgsConstructor
public class TenantTokenRefreshService {

    private final TenantAuthMechanics authMechanics;
    private final TenantAuthAuditRecorder audit;
    private final AuthRefreshSessionService refreshSessions;
    private final JsonDetailsMapper jsonDetailsMapper;

    public JwtResult refresh(String refreshToken) {

        /** comentário: valida request, executa refresh e rotaciona sessão server-side */
        if (!StringUtils.hasText(refreshToken)) {
            throw new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken é obrigatório", 400);
        }

        audit.record(
                AuthDomain.TENANT,
                AuthEventType.TOKEN_REFRESH,
                AuditOutcome.ATTEMPT,
                null,
                null,
                null,
                null,
                toJson(m("stage", "start"))
        );

        TenantRefreshIdentity id = authMechanics.resolveRefreshIdentity(refreshToken);

        JwtResult result = authMechanics.refreshTenantJwt(refreshToken);

        refreshSessions.rotateOrThrow(
                AuthSessionDomain.TENANT,
                refreshToken,
                result.refreshToken(),
                id.accountId(),
                result.userId(),
                id.tenantSchema()
        );

        audit.record(
                AuthDomain.TENANT,
                AuthEventType.TOKEN_REFRESH,
                AuditOutcome.SUCCESS,
                result.email(),
                result.userId(),
                result.accountId(),
                result.tenantSchema(),
                toJson(m("stage", "completed", "rotated", true))
        );

        return result;
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