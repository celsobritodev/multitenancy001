package brito.com.multitenancy001.tenant.auth.app;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;

import brito.com.multitenancy001.shared.auth.app.dto.JwtResult;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.AuthDomain;
import brito.com.multitenancy001.shared.domain.audit.AuthEventType;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.auth.app.audit.TenantAuthAuditRecorder;
import brito.com.multitenancy001.tenant.auth.app.boundary.TenantAuthMechanics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class TenantTokenRefreshService {

    private final TenantAuthMechanics authMechanics;
    private final TenantAuthAuditRecorder audit;

    public JwtResult refresh(String refreshToken) {

        if (!StringUtils.hasText(refreshToken)) {
            throw new ApiException(ApiErrorCode.INVALID_REFRESH, "refreshToken é obrigatório", 400);
        }

        audit.record(AuthDomain.TENANT, AuthEventType.TOKEN_REFRESH, AuditOutcome.ATTEMPT, null, null, null, null,
                "{\"stage\":\"start\"}");

        JwtResult result = authMechanics.refreshTenantJwt(refreshToken);

        audit.record(AuthDomain.TENANT, AuthEventType.TOKEN_REFRESH, AuditOutcome.SUCCESS, result.email(), result.userId(), result.accountId(), result.tenantSchema(),
                "{\"stage\":\"completed\"}");

        return result;
    }
}
