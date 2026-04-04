package brito.com.multitenancy001.controlplane.auth.app;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.infrastructure.security.AuthenticatedUserContext;
import brito.com.multitenancy001.infrastructure.security.userdetails.MultiContextUserDetailsService;
import brito.com.multitenancy001.integration.audit.ControlPlaneAuthEventAuditIntegrationService;
import brito.com.multitenancy001.integration.auth.ControlPlaneJwtIntegrationService;
import brito.com.multitenancy001.integration.auth.ControlPlaneRefreshIdentity;
import brito.com.multitenancy001.integration.auth.ControlPlaneRefreshTokenIntrospectionIntegrationService;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.auth.app.AuthRefreshSessionService;
import brito.com.multitenancy001.shared.auth.app.dto.JwtResult;
import brito.com.multitenancy001.shared.auth.domain.AuthSessionDomain;
import brito.com.multitenancy001.shared.db.Schemas;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.AuthDomain;
import brito.com.multitenancy001.shared.domain.audit.AuthEventType;
import brito.com.multitenancy001.shared.executor.PublicSchemaExecutor;
import brito.com.multitenancy001.shared.json.JsonDetailsMapper;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.security.SystemRoleName;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Refresh token do Control Plane com rotação server-side.
 *
 * <p>Regras:</p>
 * <ul>
 *   <li>Refresh emite novo refresh token (rotação).</li>
 *   <li>{@code rotateOrThrow} atualiza o hash server-side.</li>
 *   <li>Não monta JSON manualmente.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ControlPlaneTokenRefreshService {

    private static final String DEFAULT_SCHEMA = Schemas.CONTROL_PLANE;

    private final PublicSchemaExecutor publicExecutor;
    private final ControlPlaneRefreshTokenIntrospectionIntegrationService refreshIntrospection;
    private final MultiContextUserDetailsService userDetailsService;
    private final ControlPlaneJwtIntegrationService jwtIntegration;
    private final AuthRefreshSessionService refreshSessions;
    private final ControlPlaneAuthEventAuditIntegrationService authAuditService;
    private final JsonDetailsMapper jsonDetailsMapper;

    /**
     * Executa refresh token do Control Plane.
     *
     * @param refreshToken refresh token atual
     * @return novo access token + novo refresh token
     */
    public JwtResult refresh(String refreshToken) {
        if (!StringUtils.hasText(refreshToken)) {
            throw new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken é obrigatório", 400);
        }

        auditAttempt();

        return publicExecutor.inPublic(() -> {
            ControlPlaneRefreshIdentity id = refreshIntrospection.parseOrThrow(refreshToken);

            UserDetails ud = userDetailsService.loadControlPlaneUserByEmail(id.email(), id.accountId());
            Authentication auth = new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities());

            String newAccess = jwtIntegration.generateControlPlaneToken(auth, id.accountId(), DEFAULT_SCHEMA);
            String newRefresh = jwtIntegration.generateRefreshToken(id.email(), DEFAULT_SCHEMA, id.accountId());

            Long userId = (ud instanceof AuthenticatedUserContext ctx) ? ctx.getUserId() : null;
            String roleName = (ud instanceof AuthenticatedUserContext ctx) ? ctx.getRoleName() : null;
            SystemRoleName role = SystemRoleName.fromString(roleName);

            refreshSessions.rotateOrThrow(
                    AuthSessionDomain.CONTROLPLANE,
                    refreshToken,
                    newRefresh,
                    id.accountId(),
                    userId,
                    null
            );

            auditSuccess(id.email(), userId);

            log.info("Refresh token do Control Plane concluído | email={} | userId={} | accountId={}",
                    id.email(), userId, id.accountId());

            return new JwtResult(
                    newAccess,
                    newRefresh,
                    userId,
                    id.email(),
                    role,
                    id.accountId(),
                    DEFAULT_SCHEMA
            );
        });
    }

    /**
     * Registra tentativa de refresh.
     */
    private void auditAttempt() {
        authAuditService.record(
                AuthDomain.CONTROLPLANE,
                AuthEventType.TOKEN_REFRESH,
                AuditOutcome.ATTEMPT,
                null,
                null,
                null,
                DEFAULT_SCHEMA,
                toJson(m("stage", "start"))
        );
    }

    /**
     * Registra sucesso de refresh.
     *
     * @param email email do usuário
     * @param userId id do usuário
     */
    private void auditSuccess(String email, Long userId) {
        authAuditService.record(
                AuthDomain.CONTROLPLANE,
                AuthEventType.TOKEN_REFRESH,
                AuditOutcome.SUCCESS,
                email,
                null,
                userId,
                DEFAULT_SCHEMA,
                toJson(m(
                        "stage", "completed",
                        "rotated", true
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