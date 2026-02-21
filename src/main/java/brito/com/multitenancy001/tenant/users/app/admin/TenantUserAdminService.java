package brito.com.multitenancy001.tenant.users.app.admin;

import brito.com.multitenancy001.infrastructure.tenant.TenantExecutor;
import brito.com.multitenancy001.integration.security.TenantRequestIdentityService;
import brito.com.multitenancy001.tenant.users.app.command.TenantUserCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Casos de uso administrativos do Tenant para gestão de usuários.
 *
 * Regras:
 * - Resolve accountId + tenantSchema a partir da identidade do request (não usa SecurityUtils diretamente).
 * - Executa o comando dentro do schema do tenant via TenantExecutor.
 * - Não implementa regra de negócio: delega para TenantUserCommandService.
 */
@Service
@RequiredArgsConstructor
public class TenantUserAdminService {

    private final TenantUserCommandService tenantUserCommandService;
    private final TenantRequestIdentityService requestIdentity;
    private final TenantExecutor tenantExecutor;

    public void setUserSuspendedByAdmin(Long userId, boolean suspended) {
        /* Suspende/reativa usuário por ação administrativa (admin flag). */
        Long accountId = requestIdentity.getCurrentAccountId();
        String tenantSchema = requestIdentity.getCurrentTenantSchema();

        tenantExecutor.runInTenantSchema(tenantSchema, () -> {
            tenantUserCommandService.setSuspendedByAdmin(accountId, tenantSchema, userId, suspended);
            return null;
        });
    }
}