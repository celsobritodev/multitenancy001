package brito.com.multitenancy001.tenant.users.app.context;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import brito.com.multitenancy001.integration.security.TenantRequestIdentityService;
import brito.com.multitenancy001.shared.executor.TenantToPublicBridgeExecutor;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountEntitlementsGuard;
import brito.com.multitenancy001.tenant.users.app.command.TenantUserCommandService;
import brito.com.multitenancy001.tenant.users.app.query.TenantUserQueryService;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;

/**
 * Teste de regressão para garantir que o fluxo de restore
 * consulta o usuário incluindo deletados antes de restaurar.
 *
 * <p>Objetivo:</p>
 * <ul>
 *   <li>Evitar regressão em que o restore use {@code getUser(...)} em vez de
 *       {@code getUserIncludingDeleted(...)}.</li>
 *   <li>Garantir que o command de restore continue recebendo
 *       {@code userId}, {@code accountId} e {@code tenantSchema} corretos.</li>
 * </ul>
 */
public class TenantUserRestoreVerifierTest {

    /**
     * Deve consultar o usuário incluindo deletados antes de chamar o restore.
     */
    @Test
    void restoreTenantUser_mustLookupIncludingDeleted_orRestoreWillBreak() {
        // =========================================================
        // Arrange
        // =========================================================
        TenantUserCommandService commandService = mock(TenantUserCommandService.class);
        TenantUserQueryService queryService = mock(TenantUserQueryService.class);
        TenantRequestIdentityService requestIdentity = mock(TenantRequestIdentityService.class);
        AccountEntitlementsGuard entitlementsGuard = mock(AccountEntitlementsGuard.class);
        TenantToPublicBridgeExecutor tenantToPublicBridgeExecutor = mock(TenantToPublicBridgeExecutor.class);

        TenantUserCurrentContextCommandService sut = new TenantUserCurrentContextCommandService(
                commandService,
                queryService,
                requestIdentity,
                entitlementsGuard,
                tenantToPublicBridgeExecutor
        );

        Long accountId = 2L;
        String tenantSchema = "t_tenant_x";
        Long userId = 99L;

        when(requestIdentity.getCurrentAccountId()).thenReturn(accountId);
        when(requestIdentity.getCurrentTenantSchema()).thenReturn(tenantSchema);

        TenantUser target = new TenantUser();
        target.setAccountId(accountId);
        target.changeEmail("user@tenant.local");
        target.setId(userId);

        when(queryService.getUserIncludingDeleted(eq(userId), eq(accountId))).thenReturn(target);
        when(commandService.restore(eq(userId), eq(accountId), eq(tenantSchema))).thenReturn(target);

        // =========================================================
        // Act
        // =========================================================
        TenantUser restored = sut.restoreTenantUser(userId);

        // =========================================================
        // Assert
        // =========================================================
        assertSame(target, restored);

        verify(queryService, times(1)).getUserIncludingDeleted(eq(userId), eq(accountId));
        verify(queryService, never()).getUser(eq(userId), eq(accountId));

        verify(commandService, times(1)).restore(eq(userId), eq(accountId), eq(tenantSchema));

        // Neste fluxo específico, o bridge TENANT -> PUBLIC não deve ser usado.
        verify(tenantToPublicBridgeExecutor, never()).run(org.mockito.ArgumentMatchers.any());
        verify(tenantToPublicBridgeExecutor, never()).call(org.mockito.ArgumentMatchers.any());
    }
}