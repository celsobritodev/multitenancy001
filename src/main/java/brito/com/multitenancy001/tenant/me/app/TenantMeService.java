package brito.com.multitenancy001.tenant.me.app;

import brito.com.multitenancy001.infrastructure.tenant.TenantExecutor;
import brito.com.multitenancy001.integration.security.TenantRequestIdentityService;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.me.api.dto.TenantChangeMyPasswordRequest;
import brito.com.multitenancy001.tenant.me.api.dto.UpdateMyProfileRequest;
import brito.com.multitenancy001.tenant.users.app.command.TenantUserCommandService;
import brito.com.multitenancy001.tenant.users.app.query.TenantUserQueryService;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Application Service (Tenant): Me.
 *
 * Regras:
 * - Resolve accountId/userId/tenantSchema da identidade do request.
 * - Executa tudo dentro do schema via TenantExecutor.
 * - Não acessa repository direto (só services).
 */
@Service
@RequiredArgsConstructor
public class TenantMeService {

    private final TenantUserQueryService tenantUserQueryService;
    private final TenantUserCommandService tenantUserCommandService;

    private final TenantExecutor tenantExecutor;
    private final TenantRequestIdentityService requestIdentity;
    private final AppClock appClock;

    public TenantUser getMyProfile() {
        /* Retorna perfil do usuário autenticado no tenant. */
        Long accountId = requestIdentity.getCurrentAccountId();
        String tenantSchema = requestIdentity.getCurrentTenantSchema();
        Long userId = requestIdentity.getCurrentUserId();

        return tenantExecutor.runInTenantSchema(tenantSchema, () ->
                tenantUserQueryService.getUser(userId, accountId)
        );
    }

    public TenantUser updateMyProfile(UpdateMyProfileRequest req) {
        /* Atualiza dados de perfil do usuário autenticado (sem alterar role/perms). */
        if (req == null) throw new ApiException(ApiErrorCode.INVALID_REQUEST, "request é obrigatório", 400);

        Long accountId = requestIdentity.getCurrentAccountId();
        String tenantSchema = requestIdentity.getCurrentTenantSchema();
        Long userId = requestIdentity.getCurrentUserId();

        return tenantExecutor.runInTenantSchema(tenantSchema, () ->
                // ✅ FIX: assinatura correta: (userId, accountId, tenantSchema, name, phone, avatarUrl, locale, timezone, now)
                tenantUserCommandService.updateProfile(
                        userId,
                        accountId,
                        tenantSchema,
                        req.name(),
                        req.phone(),
                        req.avatarUrl(),
                        req.locale(),
                        req.timezone(),
                        appClock.instant()
                )
        );
    }

    public void changeMyPassword(TenantChangeMyPasswordRequest req) {
        /* Troca autenticada de senha (self). */
        if (req == null) throw new ApiException(ApiErrorCode.INVALID_REQUEST, "request é obrigatório", 400);

        Long accountId = requestIdentity.getCurrentAccountId();
        String tenantSchema = requestIdentity.getCurrentTenantSchema();
        Long userId = requestIdentity.getCurrentUserId();

        tenantExecutor.runInTenantSchema(tenantSchema, () -> {
            // ✅ FIX: assinatura correta: (userId, accountId, tenantSchema, currentPassword, newPassword, confirmNewPassword)
            tenantUserCommandService.changeMyPassword(
                    userId,
                    accountId,
                    tenantSchema,
                    req.currentPassword(),
                    req.newPassword(),
                    req.confirmNewPassword()
            );
            return null;
        });
    }
}