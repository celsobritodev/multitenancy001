package brito.com.multitenancy001.tenant.users.app.context;

import brito.com.multitenancy001.integration.security.TenantRequestIdentityService;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountEntitlementsGuard;
import brito.com.multitenancy001.tenant.users.app.command.TenantUserCommandService;
import brito.com.multitenancy001.tenant.users.app.query.TenantUserQueryService;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class TenantUserRestoreVerifierTest {

    @Test
    void restoreTenantUser_mustLookupIncludingDeleted_orRestoreWillBreak() {
        // mocks
        TenantUserCommandService commandService = mock(TenantUserCommandService.class);
        TenantUserQueryService queryService = mock(TenantUserQueryService.class);
        TenantRequestIdentityService requestIdentity = mock(TenantRequestIdentityService.class);
        AccountEntitlementsGuard entitlementsGuard = mock(AccountEntitlementsGuard.class);

        // sut (novo construtor - sem TenantExecutor)
        TenantUserCurrentContextCommandService sut = new TenantUserCurrentContextCommandService(
                commandService,
                queryService,
                requestIdentity,
                entitlementsGuard
        );

        // identity
        Long accountId = 2L;
        String tenantSchema = "t_tenant_x";
        Long userId = 99L;

        when(requestIdentity.getCurrentAccountId()).thenReturn(accountId);
        when(requestIdentity.getCurrentTenantSchema()).thenReturn(tenantSchema);

        // IMPORTANT: restore must lookup including deleted
        TenantUser target = new TenantUser();
        target.setAccountId(accountId);
        target.changeEmail("user@tenant.local");
        target.setId(userId);

        when(queryService.getUserIncludingDeleted(eq(userId), eq(accountId))).thenReturn(target);

        // command restores and returns restored entity (simulate)
        when(commandService.restore(eq(userId), eq(accountId), eq(tenantSchema))).thenReturn(target);

        // act
        TenantUser restored = sut.restoreTenantUser(userId);

        // assert
        assertSame(target, restored);

        // regression guard: must call getUserIncludingDeleted (NOT getUser)
        verify(queryService, times(1)).getUserIncludingDeleted(eq(userId), eq(accountId));
        verify(queryService, never()).getUser(eq(userId), eq(accountId));

        verify(commandService, times(1)).restore(eq(userId), eq(accountId), eq(tenantSchema));
    }
}