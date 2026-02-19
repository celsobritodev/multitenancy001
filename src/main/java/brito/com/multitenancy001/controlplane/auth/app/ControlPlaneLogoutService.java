package brito.com.multitenancy001.controlplane.auth.app;

import brito.com.multitenancy001.integration.audit.ControlPlaneAuthEventAuditIntegrationService;
import brito.com.multitenancy001.integration.auth.ControlPlaneRefreshIdentity;
import brito.com.multitenancy001.integration.auth.ControlPlaneRefreshTokenIntrospectionIntegrationService;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.auth.app.AuthRefreshSessionService;
import brito.com.multitenancy001.shared.db.Schemas;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.AuthDomain;
import brito.com.multitenancy001.shared.domain.audit.AuthEventType;
import brito.com.multitenancy001.shared.executor.PublicSchemaExecutor;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.persistence.publicschema.LoginIdentityResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Logout forte do Control Plane (opção B).
 *
 * Regras:
 * - Revoga refresh token no servidor (public schema)
 * - allDevices=true revoga todas as sessões do usuário no domínio CONTROLPLANE
 */
@Service
@RequiredArgsConstructor
public class ControlPlaneLogoutService {

    private static final String DEFAULT_SCHEMA = Schemas.CONTROL_PLANE;

    private final PublicSchemaExecutor publicExecutor;
    private final ControlPlaneRefreshTokenIntrospectionIntegrationService refreshIntrospection;
    private final LoginIdentityResolver loginIdentityResolver;

    private final AuthRefreshSessionService refreshSessions;
    private final ControlPlaneAuthEventAuditIntegrationService authAudit;

    public void logout(String refreshToken, boolean allDevices) {
        /** comentário: resolve identidade e revoga sessão(ões) no servidor */
        if (!StringUtils.hasText(refreshToken)) {
            throw new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken é obrigatório", 400);
        }

        publicExecutor.inPublic(() -> {
            ControlPlaneRefreshIdentity id = refreshIntrospection.parseOrThrow(refreshToken);

            Long userId = loginIdentityResolver.resolveControlPlaneUserIdByEmail(id.email());
            if (userId == null) {
                throw new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken inválido", 401);
            }

            authAudit.record(
                    AuthDomain.CONTROLPLANE,
                    AuthEventType.LOGOUT,
                    AuditOutcome.ATTEMPT,
                    id.email(),
                    null,   // ✅ actorEmail
                    userId, // ✅ actorUserId
                    DEFAULT_SCHEMA,
                    "{\"stage\":\"start\",\"allDevices\":" + allDevices + "}"
            );

            if (allDevices) {
                refreshSessions.revokeAllForUser(
                        "CONTROLPLANE",
                        id.accountId(),
                        userId,
                        "{\"reason\":\"logout_all_devices\"}"
                );
            } else {
                refreshSessions.revokeByRefreshTokenOrThrow(
                        refreshToken,
                        "{\"reason\":\"logout\"}"
                );
            }

            authAudit.record(
                    AuthDomain.CONTROLPLANE,
                    AuthEventType.LOGOUT,
                    AuditOutcome.SUCCESS,
                    id.email(),
                    null,   // ✅ actorEmail
                    userId, // ✅ actorUserId
                    DEFAULT_SCHEMA,
                    "{\"stage\":\"completed\",\"allDevices\":" + allDevices + "}"
            );

            return null;
        });
    }
}
