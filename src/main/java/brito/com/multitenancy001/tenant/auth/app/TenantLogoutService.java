package brito.com.multitenancy001.tenant.auth.app;

import brito.com.multitenancy001.infrastructure.tenant.TenantExecutor;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.auth.app.AuthRefreshSessionService;
import brito.com.multitenancy001.shared.auth.domain.AuthSessionDomain;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.AuthDomain;
import brito.com.multitenancy001.shared.domain.audit.AuthEventType;
import brito.com.multitenancy001.shared.json.JsonDetailsMapper;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.auth.app.audit.TenantAuthAuditRecorder;
import brito.com.multitenancy001.tenant.auth.app.boundary.TenantAuthMechanics;
import brito.com.multitenancy001.tenant.auth.app.boundary.TenantRefreshIdentity;
import brito.com.multitenancy001.tenant.users.persistence.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Logout forte do Tenant (opção B).
 *
 * Regras:
 * - Revoga refresh token no servidor (public schema).
 * - allDevices=true revoga todas as sessões do usuário no domínio TENANT.
 *
 * Nota:
 * - resolveRefreshIdentity(refreshToken) não faz query e pode retornar userId null.
 * - Para allDevices=true, precisamos resolver userId no schema do tenant.
 *
 * Regras de audit details:
 * - SEMPRE Map/record via JsonDetailsMapper
 * - Nunca montar JSON na mão (inclusive boolean concatenado).
 */
@Service
@RequiredArgsConstructor
public class TenantLogoutService {

    private final TenantAuthMechanics authMechanics;
    private final AuthRefreshSessionService refreshSessions;
    private final TenantAuthAuditRecorder audit;

    // ✅ necessários para resolver userId quando allDevices=true
    private final TenantExecutor tenantExecutor;
    private final TenantUserRepository tenantUserRepository;

    private final JsonDetailsMapper jsonDetailsMapper;

    public void logout(String refreshToken, boolean allDevices) {
        /** comentário: resolve identidade do refresh e revoga sessão(ões) */
        TenantRefreshIdentity id = authMechanics.resolveRefreshIdentity(refreshToken);

        audit.record(
                AuthDomain.TENANT,
                AuthEventType.LOGOUT,
                AuditOutcome.ATTEMPT,
                id.email(),
                id.userId(),
                id.accountId(),
                id.tenantSchema(),
                toJson(m("stage", "start", "allDevices", allDevices))
        );

        if (allDevices) {
            Long userId = resolveUserIdOrThrow(id);

            refreshSessions.revokeAllForUser(
                    AuthSessionDomain.TENANT,
                    id.accountId(),
                    userId,
                    toJson(m("reason", "logout_all_devices"))
            );
        } else {
            refreshSessions.revokeByRefreshTokenOrThrow(
                    refreshToken,
                    toJson(m("reason", "logout"))
            );
        }

        audit.record(
                AuthDomain.TENANT,
                AuthEventType.LOGOUT,
                AuditOutcome.SUCCESS,
                id.email(),
                id.userId(),
                id.accountId(),
                id.tenantSchema(),
                toJson(m("stage", "completed", "allDevices", allDevices))
        );
    }

    private Long resolveUserIdOrThrow(TenantRefreshIdentity id) {
        /** comentário: garante userId para revokeAllForUser (allDevices) */
        if (id.userId() != null) {
            return id.userId();
        }

        return tenantExecutor.runInTenantSchema(id.tenantSchema(), () ->
                tenantUserRepository
                        .findByEmailAndAccountIdAndDeletedFalse(id.email(), id.accountId())
                        .map(u -> u.getId())
                        .orElseThrow(() -> new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken inválido", 401))
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