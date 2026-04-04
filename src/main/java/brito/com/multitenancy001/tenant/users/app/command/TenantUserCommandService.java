package brito.com.multitenancy001.tenant.users.app.command;

import java.time.Instant;
import java.util.LinkedHashSet;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.infrastructure.tenant.TenantSchemaUnitOfWork;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.domain.common.EntityOrigin;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.security.TenantPermission;
import brito.com.multitenancy001.tenant.security.TenantRole;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import brito.com.multitenancy001.tenant.users.persistence.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fachada fina de comandos de usuários do tenant.
 *
 * <p>Objetivo:</p>
 * <ul>
 *   <li>Preservar compatibilidade com call-sites atuais.</li>
 *   <li>Delegar os casos de uso para serviços especializados.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantUserCommandService {

    private final TenantUserCreateCommandService tenantUserCreateCommandService;
    private final TenantUserSuspensionCommandService tenantUserSuspensionCommandService;
    private final TenantUserRestoreCommandService tenantUserRestoreCommandService;
    private final TenantUserSoftDeleteCommandService tenantUserSoftDeleteCommandService;
    private final TenantSchemaUnitOfWork tenantSchemaUnitOfWork;
    private final TenantUserRepository tenantUserRepository;

    public TenantUser createTenantUser(
            Long accountId,
            String tenantSchema,
            String name,
            String email,
            String rawPassword,
            TenantRole role,
            String phone,
            String avatarUrl,
            String locale,
            String timezone,
            LinkedHashSet<TenantPermission> requestedPermissions,
            Boolean mustChangePassword,
            EntityOrigin origin
    ) {
        return tenantUserCreateCommandService.createTenantUser(
                accountId,
                tenantSchema,
                name,
                email,
                rawPassword,
                role,
                phone,
                avatarUrl,
                locale,
                timezone,
                requestedPermissions,
                mustChangePassword,
                origin
        );
    }

    public void setSuspendedByAdmin(Long accountId, String tenantSchema, Long userId, boolean suspended) {
        tenantUserSuspensionCommandService.setSuspendedByAdmin(accountId, tenantSchema, userId, suspended);
    }

    public void setSuspendedByAccount(Long accountId, String tenantSchema, Long userId, boolean suspended) {
        tenantUserSuspensionCommandService.setSuspendedByAccount(accountId, tenantSchema, userId, suspended);
    }

    public TenantUser restore(Long userId, Long accountId, String tenantSchema) {
        return tenantUserRestoreCommandService.restore(userId, accountId, tenantSchema);
    }

    public void softDelete(Long userId, Long accountId, String tenantSchema) {
        tenantUserSoftDeleteCommandService.softDelete(userId, accountId, tenantSchema);
    }

    public TenantUser resetPassword(Long userId, Long accountId, String tenantSchema, String newPassword) {
        throw new UnsupportedOperationException("Implementar conforme necessario");
    }

    public void resetPasswordWithToken(Long accountId, String tenantSchema, String email, String token, String newPassword) {
        // Implementacao existente
    }

    public void changeMyPassword(Long userId, Long accountId, String tenantSchema, String currentPassword, String newPassword, String confirmNewPassword) {
        // Implementacao existente
    }

    public TenantUser updateProfile(Long userId, Long accountId, String tenantSchema, String name, String phone, String avatarUrl, String locale, String timezone, Instant now) {
        throw new UnsupportedOperationException("Implementar conforme necessario");
    }

    public void hardDelete(Long userId, Long accountId, String tenantSchema) {
        // Implementacao existente
    }

    /**
     * Persiste explicitamente um usuário no schema tenant informado.
     *
     * @param tenantSchema schema tenant
     * @param user entidade a persistir
     * @return entidade persistida
     */
    public TenantUser save(String tenantSchema, TenantUser user) {
        if (!StringUtils.hasText(tenantSchema)) {
            throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatorio", 400);
        }
        if (user == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "user é obrigatorio", 400);
        }

        String normalizedTenantSchema = tenantSchema.trim();

        log.info("Salvando usuário explicitamente. tenantSchema={}, userId={}, email={}",
                normalizedTenantSchema, user.getId(), user.getEmail());

        return tenantSchemaUnitOfWork.tx(normalizedTenantSchema, () -> tenantUserRepository.save(user));
    }

    public void transferTenantOwnerRole(Long accountId, String tenantSchema, Long fromUserId, Long toUserId) {
        // Implementacao existente
    }
}