package brito.com.multitenancy001.controlplane.auth.app;

import brito.com.multitenancy001.integration.audit.ControlPlaneAuthEventAuditIntegrationService;
import brito.com.multitenancy001.integration.auth.ControlPlaneJwtIntegrationService;
import brito.com.multitenancy001.integration.auth.ControlPlaneRefreshIdentity;
import brito.com.multitenancy001.integration.auth.ControlPlaneRefreshTokenIntrospectionIntegrationService;
import brito.com.multitenancy001.infrastructure.security.AuthenticatedUserContext;
import brito.com.multitenancy001.infrastructure.security.userdetails.MultiContextUserDetailsService;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.auth.app.AuthRefreshSessionService;
import brito.com.multitenancy001.shared.auth.app.dto.JwtResult;
import brito.com.multitenancy001.shared.db.Schemas;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.AuthDomain;
import brito.com.multitenancy001.shared.domain.audit.AuthEventType;
import brito.com.multitenancy001.shared.executor.PublicSchemaExecutor;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.security.SystemRoleName;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Refresh token do Control Plane (com rotação + logout forte).
 *
 * Regras:
 * - refresh emite NOVO refresh token (rotação)
 * - rotateOrThrow atualiza o hash server-side (logout forte depende disso)
 */
@Service
@RequiredArgsConstructor
public class ControlPlaneTokenRefreshService {

    private static final String DEFAULT_SCHEMA = Schemas.CONTROL_PLANE;

    private final PublicSchemaExecutor publicExecutor;
    private final ControlPlaneRefreshTokenIntrospectionIntegrationService refreshIntrospection;
    private final MultiContextUserDetailsService userDetailsService;
    private final ControlPlaneJwtIntegrationService jwtIntegration;

    private final AuthRefreshSessionService refreshSessions;
    private final ControlPlaneAuthEventAuditIntegrationService authAudit;

    public JwtResult refresh(String refreshToken) {
        /** comentário: valida request, emite novos tokens e rotaciona sessão server-side */
        if (!StringUtils.hasText(refreshToken)) {
            throw new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken é obrigatório", 400);
        }

        authAudit.record(
                AuthDomain.CONTROLPLANE,
                AuthEventType.TOKEN_REFRESH,
                AuditOutcome.ATTEMPT,
                null,
                null,   // ✅ actorEmail
                null,   // actorUserId
                DEFAULT_SCHEMA,
                "{\"stage\":\"start\"}"
        );

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
                    "CONTROLPLANE",
                    refreshToken,
                    newRefresh,
                    id.accountId(),
                    userId,
                    DEFAULT_SCHEMA
            );

            authAudit.record(
                    AuthDomain.CONTROLPLANE,
                    AuthEventType.TOKEN_REFRESH,
                    AuditOutcome.SUCCESS,
                    id.email(),
                    null,   // ✅ actorEmail
                    userId, // ✅ actorUserId
                    DEFAULT_SCHEMA,
                    "{\"stage\":\"completed\",\"rotated\":true}"
            );

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
}
