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

    public void logout(String refreshToken, boolean allDevices) {

        if (!StringUtils.hasText(refreshToken)) {
            throw new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken é obrigatório");
        }

        publicSchemaExecutor.inPublic(() -> {

            ControlPlaneRefreshIdentity id = refreshIntrospection.parseOrThrow(refreshToken);

            Long userId = loginIdentityResolver.resolveControlPlaneUserIdByEmail(id.email());
            if (userId == null) {
                throw new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken inválido");
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

            log.info("Logout concluído | email={} | userId={} | accountId={} | allDevices={}",
                    id.email(), userId, id.accountId(), allDevices);

            return null;
        });
    }

    private void auditAttempt(String email, Long userId, boolean allDevices) {
        authAuditService.record(
                AuthDomain.CONTROLPLANE,
                AuthEventType.LOGOUT,
                AuditOutcome.ATTEMPT,
                email,
                null,
                userId,
                DEFAULT_SCHEMA,
                toJson(m("stage", "start", "allDevices", allDevices))
        );
    }

    private void auditSuccess(String email, Long userId, boolean allDevices) {
        authAuditService.record(
                AuthDomain.CONTROLPLANE,
                AuthEventType.LOGOUT,
                AuditOutcome.SUCCESS,
                email,
                null,
                userId,
                DEFAULT_SCHEMA,
                toJson(m("stage", "completed", "allDevices", allDevices))
        );
    }

    private Map<String, Object> m(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }

    private String toJson(Map<String, Object> details) {
        return jsonDetailsMapper.toJson(details);
    }
}