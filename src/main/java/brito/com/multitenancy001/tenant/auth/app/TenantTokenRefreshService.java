package brito.com.multitenancy001.tenant.auth.app;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.auth.app.AuthRefreshSessionService;
import brito.com.multitenancy001.shared.auth.app.dto.JwtResult;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.AuthDomain;
import brito.com.multitenancy001.shared.domain.audit.AuthEventType;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.auth.app.audit.TenantAuthAuditRecorder;
import brito.com.multitenancy001.tenant.auth.app.boundary.TenantAuthMechanics;
import brito.com.multitenancy001.tenant.auth.app.boundary.TenantRefreshIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Serviço de refresh token do Tenant.
 *
 * Regra:
 * - refresh rotaciona o refresh token no servidor (logout forte depende disso)
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
                "{\"stage\":\"start\"}"
        );

        TenantRefreshIdentity id = authMechanics.resolveRefreshIdentity(refreshToken);

        JwtResult result = authMechanics.refreshTenantJwt(refreshToken);

        refreshSessions.rotateOrThrow(
                "TENANT",
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
                "{\"stage\":\"completed\",\"rotated\":true}"
        );

        return result;
    }
}
