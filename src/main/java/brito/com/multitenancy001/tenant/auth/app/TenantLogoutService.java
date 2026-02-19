package brito.com.multitenancy001.tenant.auth.app;

import brito.com.multitenancy001.shared.auth.app.AuthRefreshSessionService;
import brito.com.multitenancy001.shared.domain.audit.AuditOutcome;
import brito.com.multitenancy001.shared.domain.audit.AuthDomain;
import brito.com.multitenancy001.shared.domain.audit.AuthEventType;
import brito.com.multitenancy001.tenant.auth.app.audit.TenantAuthAuditRecorder;
import brito.com.multitenancy001.tenant.auth.app.boundary.TenantAuthMechanics;
import brito.com.multitenancy001.tenant.auth.app.boundary.TenantRefreshIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Logout forte do Tenant (opção B).
 *
 * Regras:
 * - Revoga refresh token no servidor (public schema)
 * - allDevices=true revoga todas as sessões do usuário no domínio TENANT
 */
@Service
@RequiredArgsConstructor
public class TenantLogoutService {

    private final TenantAuthMechanics authMechanics;
    private final AuthRefreshSessionService refreshSessions;
    private final TenantAuthAuditRecorder audit;

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
                "{\"stage\":\"start\",\"allDevices\":" + allDevices + "}"
        );

        if (allDevices) {
            refreshSessions.revokeAllForUser(
                    "TENANT",
                    id.accountId(),
                    id.userId(),
                    "{\"reason\":\"logout_all_devices\"}"
            );
        } else {
            refreshSessions.revokeByRefreshTokenOrThrow(
                    refreshToken,
                    "{\"reason\":\"logout\"}"
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
                "{\"stage\":\"completed\",\"allDevices\":" + allDevices + "}"
        );
    }
}
