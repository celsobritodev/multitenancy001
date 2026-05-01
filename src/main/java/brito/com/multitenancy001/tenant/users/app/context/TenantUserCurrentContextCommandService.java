package brito.com.multitenancy001.tenant.users.app.context;

import java.util.LinkedHashSet;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.integration.security.TenantRequestIdentityService;
import brito.com.multitenancy001.shared.account.UserLimitPolicy;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.domain.common.EntityOrigin;
import brito.com.multitenancy001.shared.executor.TenantToPublicBridgeExecutor;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountEntitlementsGuard;
import brito.com.multitenancy001.shared.validation.RequiredValidator;
import brito.com.multitenancy001.shared.validation.TenantContextValidator;
import brito.com.multitenancy001.shared.validation.TextValidator;
import brito.com.multitenancy001.tenant.security.TenantPermission;
import brito.com.multitenancy001.tenant.users.api.dto.TenantUserCreateRequest;
import brito.com.multitenancy001.tenant.users.app.command.TenantUserCommandService;
import brito.com.multitenancy001.tenant.users.app.query.TenantUserQueryService;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Application Service (Tenant): users no contexto atual da identidade do request.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Resolver accountId, tenantSchema e userId da identidade atual.</li>
 *   <li>Aplicar guard de entitlements antes da criação.</li>
 *   <li>Delegar writes para {@link TenantUserCommandService}.</li>
 *   <li>Garantir mensagens amigáveis sem espalhar validação inline.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantUserCurrentContextCommandService {

    private final TenantUserCommandService tenantUserCommandService;
    private final TenantUserQueryService tenantUserQueryService;
    private final TenantRequestIdentityService requestIdentity;
    private final AccountEntitlementsGuard accountEntitlementsGuard;
    private final TenantToPublicBridgeExecutor tenantToPublicBridgeExecutor;

    /**
     * Transfere ownership do tenant do usuário autenticado para outro usuário.
     *
     * @param toUserId usuário de destino
     */
    public void transferTenantOwner(Long toUserId) {
        Long accountId = requestIdentity.getCurrentAccountId();
        String tenantSchema = requestIdentity.getCurrentTenantSchema();
        Long fromUserId = requestIdentity.getCurrentUserId();

        TenantContextValidator.requireAccountTenantAndUser(accountId, tenantSchema, fromUserId);
        RequiredValidator.requireToUserId(toUserId);

        if (fromUserId.equals(toUserId)) {
            log.warn(
                    "❌ Transferência inválida de ownership | accountId={} | tenantSchema={} | fromUserId={} | toUserId={}",
                    accountId,
                    tenantSchema,
                    fromUserId,
                    toUserId
            );
            throw new ApiException(ApiErrorCode.INVALID_TRANSFER, "Não é possível transferir para si mesmo", 400);
        }

        log.info(
                "🔄 Transferência de ownership iniciada | accountId={} | tenantSchema={} | fromUserId={} | toUserId={}",
                accountId,
                tenantSchema,
                fromUserId,
                toUserId
        );

        tenantUserQueryService.getUser(toUserId, accountId);
        tenantUserCommandService.transferTenantOwnerRole(accountId, tenantSchema, fromUserId, toUserId);

        log.info(
                "✅ Transferência de ownership concluída | accountId={} | tenantSchema={} | fromUserId={} | toUserId={}",
                accountId,
                tenantSchema,
                fromUserId,
                toUserId
        );
    }

    /**
     * Cria usuário no tenant atual com verificação de entitlements.
     *
     * @param req request de criação
     * @return usuário criado
     */
    public TenantUser createTenantUser(TenantUserCreateRequest req) {
        Long accountId = requestIdentity.getCurrentAccountId();
        String tenantSchema = requestIdentity.getCurrentTenantSchema();

        TenantContextValidator.requireAccountAndTenant(accountId, tenantSchema);
        RequiredValidator.requirePayload(req, ApiErrorCode.INVALID_REQUEST, "Request inválido");

        String name = TextValidator.normalizeNullable(req.name());
        String email = TextValidator.normalizeNullable(req.email());
        String locale = TextValidator.normalizeNullable(req.locale());
        String timezone = TextValidator.normalizeNullable(req.timezone());

        LinkedHashSet<TenantPermission> perms =
                (req.permissions() == null || req.permissions().isEmpty())
                        ? null
                        : new LinkedHashSet<>(req.permissions());

        EntityOrigin origin = (req.origin() != null) ? req.origin() : EntityOrigin.ADMIN;
        if (origin == EntityOrigin.BUILT_IN) {
            log.warn(
                    "❌ Origem inválida na criação de usuário tenant | accountId={} | tenantSchema={} | origin={}",
                    accountId,
                    tenantSchema,
                    origin
            );
            throw new ApiException(ApiErrorCode.INVALID_ORIGIN, "Origin BUILT_IN não pode ser criado via API", 400);
        }

        Boolean mustChangePassword = req.mustChangePassword() == null ? Boolean.FALSE : req.mustChangePassword();

        log.info(
                "🔄 Iniciando createTenantUser no contexto atual | accountId={} | tenantSchema={} | email={} | role={}",
                accountId,
                tenantSchema,
                email,
                req.role()
        );

        long currentUsers = tenantUserQueryService.countUsersForLimit(accountId, UserLimitPolicy.SEATS_IN_USE);
        tenantToPublicBridgeExecutor.run(() ->
                accountEntitlementsGuard.assertCanCreateUser(accountId, currentUsers)
        );

        TenantUser created = tenantUserCommandService.createTenantUser(
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

        log.info(
                "✅ Usuário tenant criado no contexto atual | accountId={} | tenantSchema={} | createdUserId={}",
                accountId,
                tenantSchema,
                created.getId()
        );

        return created;
    }

    /**
     * Atualiza flag suspendedByAdmin do usuário.
     *
     * @param userId id do usuário
     * @param suspended novo estado
     * @return usuário atualizado
     */
    public TenantUser setTenantUserSuspendedByAdmin(Long userId, boolean suspended) {
        Long accountId = requestIdentity.getCurrentAccountId();
        String tenantSchema = requestIdentity.getCurrentTenantSchema();

        TenantContextValidator.requireAccountAndTenant(accountId, tenantSchema);
        RequiredValidator.requireUserId(userId);

        log.info(
                "🔄 Alterando suspendedByAdmin | accountId={} | tenantSchema={} | userId={} | suspended={}",
                accountId,
                tenantSchema,
                userId,
                suspended
        );

        tenantUserCommandService.setSuspendedByAdmin(accountId, tenantSchema, userId, suspended);
        return tenantUserQueryService.getUser(userId, accountId);
    }

    /**
     * Atualiza flag suspendedByAccount do usuário.
     *
     * @param userId id do usuário
     * @param suspended novo estado
     * @return usuário atualizado
     */
    public TenantUser setTenantUserSuspendedByAccount(Long userId, boolean suspended) {
        Long accountId = requestIdentity.getCurrentAccountId();
        String tenantSchema = requestIdentity.getCurrentTenantSchema();

        TenantContextValidator.requireAccountAndTenant(accountId, tenantSchema);
        RequiredValidator.requireUserId(userId);

        log.info(
                "🔄 Alterando suspendedByAccount | accountId={} | tenantSchema={} | userId={} | suspended={}",
                accountId,
                tenantSchema,
                userId,
                suspended
        );

        tenantUserCommandService.setSuspendedByAccount(accountId, tenantSchema, userId, suspended);
        return tenantUserQueryService.getUser(userId, accountId);
    }

    /**
     * Soft delete de usuário no contexto atual.
     *
     * @param userId id do usuário
     */
    public void softDeleteTenantUser(Long userId) {
        Long accountId = requestIdentity.getCurrentAccountId();
        String tenantSchema = requestIdentity.getCurrentTenantSchema();

        TenantContextValidator.requireAccountAndTenant(accountId, tenantSchema);
        RequiredValidator.requireUserId(userId);

        log.info(
                "🗑️ Soft delete de usuário tenant | accountId={} | tenantSchema={} | userId={}",
                accountId,
                tenantSchema,
                userId
        );

        tenantUserCommandService.softDelete(userId, accountId, tenantSchema);
    }

    /**
     * Hard delete de usuário no contexto atual.
     *
     * @param userId id do usuário
     */
    public void hardDeleteTenantUser(Long userId) {
        Long accountId = requestIdentity.getCurrentAccountId();
        String tenantSchema = requestIdentity.getCurrentTenantSchema();

        TenantContextValidator.requireAccountAndTenant(accountId, tenantSchema);
        RequiredValidator.requireUserId(userId);

        log.info(
                "🔥 Hard delete de usuário tenant | accountId={} | tenantSchema={} | userId={}",
                accountId,
                tenantSchema,
                userId
        );

        tenantUserCommandService.hardDelete(userId, accountId, tenantSchema);
    }

    /**
     * Restaura usuário previamente removido.
     *
     * @param userId id do usuário
     * @return usuário restaurado
     */
    public TenantUser restoreTenantUser(Long userId) {
        Long accountId = requestIdentity.getCurrentAccountId();
        String tenantSchema = requestIdentity.getCurrentTenantSchema();

        TenantContextValidator.requireAccountAndTenant(accountId, tenantSchema);
        RequiredValidator.requireUserId(userId);

        log.info(
                "♻️ Restore de usuário tenant | accountId={} | tenantSchema={} | userId={}",
                accountId,
                tenantSchema,
                userId
        );

        tenantUserQueryService.getUserIncludingDeleted(userId, accountId);
        return tenantUserCommandService.restore(userId, accountId, tenantSchema);
    }

    /**
     * Reseta a senha de um usuário no contexto atual.
     *
     * @param userId id do usuário
     * @param newPassword nova senha
     * @return usuário atualizado
     */
    public TenantUser resetTenantUserPassword(Long userId, String newPassword) {
        Long accountId = requestIdentity.getCurrentAccountId();
        String tenantSchema = requestIdentity.getCurrentTenantSchema();

        TenantContextValidator.requireAccountAndTenant(accountId, tenantSchema);
        RequiredValidator.requireUserId(userId);
        TextValidator.requireNewPassword(newPassword);

        log.info(
                "🔐 Reset de senha de usuário tenant | accountId={} | tenantSchema={} | userId={}",
                accountId,
                tenantSchema,
                userId
        );

        return tenantUserCommandService.resetPassword(userId, accountId, tenantSchema, newPassword);
    }
}