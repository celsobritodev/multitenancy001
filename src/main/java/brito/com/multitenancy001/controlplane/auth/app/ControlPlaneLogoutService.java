package brito.com.multitenancy001.controlplane.auth.app;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.integration.audit.ControlPlaneAuthEventAuditIntegrationService;
import brito.com.multitenancy001.integration.auth.ControlPlaneRefreshIdentity;
import brito.com.multitenancy001.integration.auth.ControlPlaneRefreshTokenIntrospectionIntegrationService;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.auth.app.AuthRefreshSessionService;
import brito.com.multitenancy001.shared.auth.domain.AuthSessionDomain;
import brito.com.multitenancy001.shared.db.Schemas;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.AuthDomain;
import brito.com.multitenancy001.shared.domain.audit.AuthEventType;
import brito.com.multitenancy001.shared.executor.PublicSchemaExecutor;
import brito.com.multitenancy001.shared.json.JsonDetailsMapper;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.persistence.publicschema.LoginIdentityFinder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Logout forte do Control Plane.
 *
 * <p>Regras:</p>
 * <ul>
 *   <li>Revoga refresh token no servidor (public schema).</li>
 *   <li>{@code allDevices=true} revoga todas as sessões do usuário no domínio CONTROLPLANE.</li>
 *   <li>Não monta JSON manualmente.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ControlPlaneLogoutService {

    private static final String DEFAULT_SCHEMA = Schemas.CONTROL_PLANE;

    private final PublicSchemaExecutor publicSchemaExecutor;
    private final ControlPlaneRefreshTokenIntrospectionIntegrationService refreshIntrospection;
    private final LoginIdentityFinder loginIdentityResolver;
    private final AuthRefreshSessionService refreshSessions;
    private final ControlPlaneAuthEventAuditIntegrationService authAuditService;
    private final JsonDetailsMapper jsonDetailsMapper;

    /**
     * Executa logout forte do Control Plane.
     *
     * @param refreshToken refresh token a ser revogado
     * @param allDevices true para revogar todas as sessões do usuário
     */
    public void logout(String refreshToken, boolean allDevices) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken é obrigatório", 400);
        }

        publicSchemaExecutor.inPublic(() -> {
            ControlPlaneRefreshIdentity id = refreshIntrospection.parseOrThrow(refreshToken);

            Long userId = loginIdentityResolver.resolveControlPlaneUserIdByEmail(id.email());
            if (userId == null) {
                throw new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken inválido", 401);
            }

            auditAttempt(id.email(), userId, allDevices);

            if (allDevices) {
                refreshSessions.revokeAllForUser(
                        AuthSessionDomain.CONTROLPLANE,
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

            auditSuccess(id.email(), userId, allDevices);

            log.info("Logout do Control Plane concluído | email={} | userId={} | accountId={} | allDevices={}",
                    id.email(), userId, id.accountId(), allDevices);

            return null;
        });
    }

    /**
     * Registra tentativa de logout.
     *
     * @param email email alvo
     * @param userId id do usuário
     * @param allDevices flag de logout global
     */
    private void auditAttempt(String email, Long userId, boolean allDevices) {
        authAuditService.record(
                AuthDomain.CONTROLPLANE,
                AuthEventType.LOGOUT,
                AuditOutcome.ATTEMPT,
                email,
                null,
                userId,
                DEFAULT_SCHEMA,
                toJson(m(
                        "stage", "start",
                        "allDevices", allDevices
                ))
        );
    }

    /**
     * Registra sucesso de logout.
     *
     * @param email email alvo
     * @param userId id do usuário
     * @param allDevices flag de logout global
     */
    private void auditSuccess(String email, Long userId, boolean allDevices) {
        authAuditService.record(
                AuthDomain.CONTROLPLANE,
                AuthEventType.LOGOUT,
                AuditOutcome.SUCCESS,
                email,
                null,
                userId,
                DEFAULT_SCHEMA,
                toJson(m(
                        "stage", "completed",
                        "allDevices", allDevices
                ))
        );
    }

    /**
     * Monta mapa ordenado a partir de pares chave/valor.
     *
     * @param kv pares chave/valor
     * @return mapa ordenado
     */
    private Map<String, Object> m(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        if (kv == null) {
            return map;
        }

        for (int i = 0; i + 1 < kv.length; i += 2) {
            Object key = kv[i];
            Object value = kv[i + 1];
            if (key != null) {
                map.put(String.valueOf(key), value);
            }
        }

        return map;
    }

    /**
     * Serializa details estruturados para JSON.
     *
     * @param details detalhes estruturados
     * @return json serializado
     */
    private String toJson(Map<String, Object> details) {
        return details == null ? null : jsonDetailsMapper.toJsonNode(details).toString();
    }
}