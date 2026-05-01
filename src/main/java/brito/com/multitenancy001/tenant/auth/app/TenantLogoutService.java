package brito.com.multitenancy001.tenant.auth.app;

import brito.com.multitenancy001.infrastructure.tenant.TenantContextExecutor;
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
 * <p>Regras:</p>
 * <ul>
 *   <li>Revoga refresh token no servidor (public schema).</li>
 *   <li>allDevices=true revoga todas as sessões do usuário no domínio TENANT.</li>
 * </ul>
 *
 * <p>Nota:</p>
 * <ul>
 *   <li>resolveRefreshIdentity(refreshToken) não faz query e pode retornar userId null.</li>
 *   <li>Para allDevices=true, precisamos resolver userId no schema do tenant.</li>
 * </ul>
 *
 * <p>Regras de audit details:</p>
 * <ul>
 *   <li>SEMPRE Map/record via JsonDetailsMapper</li>
 *   <li>Nunca montar JSON na mão (inclusive boolean concatenado)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class TenantLogoutService {

    private final TenantAuthMechanics authMechanics;
    private final AuthRefreshSessionService refreshSessions;
    private final TenantAuthAuditRecorder audit;

    private final TenantContextExecutor tenantExecutor;
    private final TenantUserRepository tenantUserRepository;

    private final JsonDetailsMapper jsonDetailsMapper;

    /**
     * Executa logout do usuário tenant.
     *
     * @param refreshToken token de refresh
     * @param allDevices indica se deve revogar todas as sessões
     */
    public void logout(String refreshToken, boolean allDevices) {

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

    /**
     * Resolve o userId necessário para revokeAllForUser.
     *
     * @param id identidade do refresh token
     * @return userId resolvido
     */
    private Long resolveUserIdOrThrow(TenantRefreshIdentity id) {

        if (id.userId() != null) {
            return id.userId();
        }

        return tenantExecutor.runInTenantSchema(id.tenantSchema(), () ->
                tenantUserRepository
                        .findByEmailAndAccountIdAndDeletedFalse(id.email(), id.accountId())
                        .map(u -> u.getId())
                        .orElseThrow(() -> new ApiException(
                                ApiErrorCode.INVALID_REFRESH,
                                "refreshToken inválido"
                        ))
        );
    }

    /**
     * Converte details para JSON string.
     *
     * @param details detalhes estruturados
     * @return json serializado
     */
    private String toJson(Object details) {
        if (details == null) return null;
        return jsonDetailsMapper.toJsonNode(details).toString();
    }

    /**
     * Cria mapa ordenado a partir de pares chave/valor.
     *
     * @param kv pares chave/valor
     * @return mapa ordenado
     */
    private static Map<String, Object> m(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (kv == null) return m;
        if (kv.length % 2 != 0) {
            throw new IllegalArgumentException("m(kv): quantidade ímpar de argumentos");
        }
        for (int i = 0; i < kv.length; i += 2) {
            Object k = kv[i];
            Object v = kv[i + 1];
            if (k == null) continue;
            m.put(String.valueOf(k), v);
        }
        return m;
    }
}