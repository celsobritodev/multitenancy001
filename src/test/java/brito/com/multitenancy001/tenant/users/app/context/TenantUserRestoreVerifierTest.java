package brito.com.multitenancy001.tenant.users.app.context;

import brito.com.multitenancy001.infrastructure.tenant.TenantExecutor;
import brito.com.multitenancy001.integration.security.TenantRequestIdentityService;
import brito.com.multitenancy001.shared.persistence.publicschema.AccountEntitlementsGuard;
import brito.com.multitenancy001.tenant.users.app.audit.TenantUserSecurityAuditRecorder;
import brito.com.multitenancy001.tenant.users.app.command.TenantUserCommandService;
import brito.com.multitenancy001.tenant.users.app.query.TenantUserQueryService;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class TenantUserRestoreVerifierTest {

    @Test
    @SuppressWarnings("unchecked")
    void restoreTenantUser_mustLookupIncludingDeleted_orRestoreWillBreak() {
        // mocks
        TenantUserCommandService commandService = mock(TenantUserCommandService.class);
        TenantUserQueryService queryService = mock(TenantUserQueryService.class);
        TenantExecutor tenantExecutor = mock(TenantExecutor.class);
        TenantRequestIdentityService requestIdentity = mock(TenantRequestIdentityService.class);
        AccountEntitlementsGuard entitlementsGuard = mock(AccountEntitlementsGuard.class);
        TenantUserSecurityAuditRecorder audit = mock(TenantUserSecurityAuditRecorder.class);

        // sut (system under test)
        TenantUserCurrentContextCommandService sut = new TenantUserCurrentContextCommandService(
                commandService,
                queryService,
                tenantExecutor,
                requestIdentity,
                entitlementsGuard,
                audit
        );

        // identity
        Long accountId = 2L;
        String tenantSchema = "t_tenant_x";
        Long userId = 99L;

        when(requestIdentity.getCurrentAccountId()).thenReturn(accountId);
        when(requestIdentity.getCurrentTenantSchema()).thenReturn(tenantSchema);

        // Mock do TenantExecutor - versão simplificada
        when(tenantExecutor.runInTenantSchema(eq(tenantSchema), any(Supplier.class)))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());

        // IMPORTANT: restore must lookup including deleted
        TenantUser target = new TenantUser();
        target.setAccountId(accountId);
        target.changeEmail("user@tenant.local");
        target.setId(userId);

        when(queryService.getUserIncludingDeleted(eq(userId), eq(accountId))).thenReturn(target);

        // command restores and returns restored entity (simulate)
        when(commandService.restore(eq(userId), eq(accountId), eq(tenantSchema))).thenReturn(target);

        // audit details stubs (minimal)
        when(audit.baseDetails(anyString(), any(), any())).thenReturn(new LinkedHashMap<>(Map.of()));

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