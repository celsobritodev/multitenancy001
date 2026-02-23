package brito.com.multitenancy001.tenant.users.app.admin;

import brito.com.multitenancy001.integration.security.TenantRequestIdentityService;
import brito.com.multitenancy001.tenant.users.app.command.TenantUserCommandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Casos de uso administrativos do Tenant para gestão de usuários.
 *
 * Regras:
 * - Resolve accountId + tenantSchema a partir da identidade do request.
 * - Não faz bind de schema nem transação aqui.
 *   (isso é responsabilidade do TenantUserCommandService via TenantSchemaUnitOfWork).
 * - Não implementa regra de negócio: delega para TenantUserCommandService.
 */
@Service
@RequiredArgsConstructor
public class TenantUserAdminService {

    private final TenantUserCommandService tenantUserCommandService;
    private final TenantRequestIdentityService requestIdentity;

    public void setUserSuspendedByAdmin(Long userId, boolean suspended) {
        /* Suspende/reativa usuário por ação administrativa (admin flag). */
        Long accountId = requestIdentity.getCurrentAccountId();
        String tenantSchema = requestIdentity.getCurrentTenantSchema();

        tenantUserCommandService.setSuspendedByAdmin(accountId, tenantSchema, userId, suspended);
    }
}