// src/main/java/brito/com/multitenancy001/tenant/users/app/context/TenantUserCurrentContextCommandService.java
package brito.com.multitenancy001.tenant.users.app.context;

import brito.com.multitenancy001.integration.security.TenantRequestIdentityService;
import brito.com.multitenancy001.shared.account.UserLimitPolicy;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.domain.common.EntityOrigin;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountEntitlementsGuard;
import brito.com.multitenancy001.tenant.security.TenantPermission;
import brito.com.multitenancy001.tenant.users.api.dto.TenantUserCreateRequest;
import brito.com.multitenancy001.tenant.users.app.command.TenantUserCommandService;
import brito.com.multitenancy001.tenant.users.app.query.TenantUserQueryService;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;

/**
 * Application Service (Tenant): Users no "current context" (identidade do request).
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Resolver accountId/userId/tenantSchema da identidade do request.</li>
 *   <li>Aplicar entitlements/limites antes de criar (ex: seats).</li>
 *   <li>Delegar writes para {@link TenantUserCommandService}.</li>
 * </ul>
 *
 * <p>Auditoria:</p>
 * <ul>
 *   <li>É responsabilidade do {@link TenantUserCommandService} (SOC2-like, via PublicAuditDispatcher).</li>
 *   <li>Este service não grava auditoria para evitar duplicidade e nesting ilegal TENANT->PUBLIC.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class TenantUserCurrentContextCommandService {

    private final TenantUserCommandService tenantUserCommandService;
    private final TenantUserQueryService tenantUserQueryService;

    private final TenantRequestIdentityService requestIdentity;
    private final AccountEntitlementsGuard accountEntitlementsGuard;

    /**
     * Transfere ownership do tenant do usuário autenticado (from) para outro usuário (to).
     *
     * <p>Semântica:</p>
     * <ul>
     *   <li>Valida identidade atual (accountId/tenantSchema/userId).</li>
     *   <li>Garante que o "toUser" existe/está habilitado (mantendo sua semântica de validação prévia).</li>
     *   <li>Delegação do write + auditoria para {@link TenantUserCommandService}.</li>
     * </ul>
     */
    public void transferTenantOwner(Long toUserId) {
        Long accountId = requestIdentity.getCurrentAccountId();
        String tenantSchema = requestIdentity.getCurrentTenantSchema();
        Long fromUserId = requestIdentity.getCurrentUserId();

        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);
        if (!StringUtils.hasText(tenantSchema)) throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatório", 400);
        if (fromUserId == null) throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId (from) é obrigatório", 400);
        if (toUserId == null) throw new ApiException(ApiErrorCode.TO_USER_REQUIRED, "toUserId é obrigatório", 400);
        if (fromUserId.equals(toUserId)) throw new ApiException(ApiErrorCode.INVALID_TRANSFER, "Não é possível transferir para si mesmo", 400);

        // Apenas valida que existe/está habilitado (mantém semântica do seu fluxo).
        tenantUserQueryService.getUser(toUserId, accountId);

        tenantUserCommandService.transferTenantOwnerRole(accountId, tenantSchema, fromUserId, toUserId);
    }

    /**
     * Cria usuário no tenant atual, aplicando guard de entitlements/limite antes do write.
     */
    public TenantUser createTenantUser(TenantUserCreateRequest req) {
        Long accountId = requestIdentity.getCurrentAccountId();
        String tenantSchema = requestIdentity.getCurrentTenantSchema();

        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);
        if (!StringUtils.hasText(tenantSchema)) throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatório", 400);

        if (req == null) throw new ApiException(ApiErrorCode.INVALID_REQUEST, "Request inválido", 400);

        String name = (req.name() == null) ? null : req.name().trim();
        String email = (req.email() == null) ? null : req.email().trim();

        String locale = (req.locale() == null) ? null : req.locale().trim();
        if (locale != null && locale.isBlank()) locale = null;

        String timezone = (req.timezone() == null) ? null : req.timezone().trim();
        if (timezone != null && timezone.isBlank()) timezone = null;

        LinkedHashSet<TenantPermission> perms =
                (req.permissions() == null || req.permissions().isEmpty())
                        ? null
                        : new LinkedHashSet<>(req.permissions());

        EntityOrigin origin = (req.origin() != null) ? req.origin() : EntityOrigin.ADMIN;
        if (origin == EntityOrigin.BUILT_IN) {
            throw new ApiException(ApiErrorCode.INVALID_ORIGIN, "Origin BUILT_IN não pode ser criado via API", 400);
        }

        Boolean mustChangePassword = (req.mustChangePassword() == null) ? Boolean.FALSE : req.mustChangePassword();

        long currentUsers = tenantUserQueryService.countUsersForLimit(accountId, UserLimitPolicy.SEATS_IN_USE);
        accountEntitlementsGuard.assertCanCreateUser(accountId, currentUsers);

        return tenantUserCommandService.createTenantUser(
                accountId,
                tenantSchema,
                name,
                email,
                req.password(),
                req.role(),
                req.phone(),
                req.avatarUrl(),
                locale,
                timezone,
                perms,
                mustChangePassword,
                origin
        );
    }

    public TenantUser setTenantUserSuspendedByAdmin(Long userId, boolean suspended) {
        Long accountId = requestIdentity.getCurrentAccountId();
        String tenantSchema = requestIdentity.getCurrentTenantSchema();

        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);
        if (!StringUtils.hasText(tenantSchema)) throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatório", 400);
        if (userId == null) throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatório", 400);

        tenantUserCommandService.setSuspendedByAdmin(accountId, tenantSchema, userId, suspended);
        return tenantUserQueryService.getUser(userId, accountId);
    }

    public TenantUser setTenantUserSuspendedByAccount(Long userId, boolean suspended) {
        Long accountId = requestIdentity.getCurrentAccountId();
        String tenantSchema = requestIdentity.getCurrentTenantSchema();

        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);
        if (!StringUtils.hasText(tenantSchema)) throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatório", 400);
        if (userId == null) throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatório", 400);

        tenantUserCommandService.setSuspendedByAccount(accountId, tenantSchema, userId, suspended);
        return tenantUserQueryService.getUser(userId, accountId);
    }

    public void softDeleteTenantUser(Long userId) {
        Long accountId = requestIdentity.getCurrentAccountId();
        String tenantSchema = requestIdentity.getCurrentTenantSchema();

        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);
        if (!StringUtils.hasText(tenantSchema)) throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatório", 400);
        if (userId == null) throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatório", 400);

        tenantUserCommandService.softDelete(userId, accountId, tenantSchema);
    }

    public void hardDeleteTenantUser(Long userId) {
        Long accountId = requestIdentity.getCurrentAccountId();
        String tenantSchema = requestIdentity.getCurrentTenantSchema();

        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);
        if (!StringUtils.hasText(tenantSchema)) throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatório", 400);
        if (userId == null) throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatório", 400);

        tenantUserCommandService.hardDelete(userId, accountId, tenantSchema);
    }

    public TenantUser restoreTenantUser(Long userId) {
        Long accountId = requestIdentity.getCurrentAccountId();
        String tenantSchema = requestIdentity.getCurrentTenantSchema();

        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);
        if (!StringUtils.hasText(tenantSchema)) throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatório", 400);
        if (userId == null) throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatório", 400);

        // precisa enxergar o deletado para restaurar (mantém sua semântica de validação)
        tenantUserQueryService.getUserIncludingDeleted(userId, accountId);

        return tenantUserCommandService.restore(userId, accountId, tenantSchema);
    }

    public TenantUser resetTenantUserPassword(Long userId, String newPassword) {
        if (!StringUtils.hasText(newPassword)) throw new ApiException(ApiErrorCode.INVALID_PASSWORD, "Nova senha é obrigatória", 400);

        Long accountId = requestIdentity.getCurrentAccountId();
        String tenantSchema = requestIdentity.getCurrentTenantSchema();

        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);
        if (!StringUtils.hasText(tenantSchema)) throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatório", 400);
        if (userId == null) throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatório", 400);

        return tenantUserCommandService.resetPassword(userId, accountId, tenantSchema, newPassword);
    }
}