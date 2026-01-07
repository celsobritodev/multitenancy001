package brito.com.multitenancy001.tenant.api.users.admin;

import brito.com.multitenancy001.controlplane.application.AccountLifecycleService;
import brito.com.multitenancy001.shared.security.AuthenticatedUserContext;
import brito.com.multitenancy001.tenant.api.dto.users.admin.TenantUserAdminSuspendRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tenant/users")
@PreAuthorize("hasRole('TENANT_ADMIN')")
@RequiredArgsConstructor
public class TenantUserAdminController {

    private final AccountLifecycleService accountProvisioningService;

    @PatchMapping("/{userId}/suspend")
    public ResponseEntity<Void> suspendUser(
            @PathVariable Long userId,
            @RequestBody TenantUserAdminSuspendRequest req,
            @AuthenticationPrincipal AuthenticatedUserContext me
    ) {
        accountProvisioningService.setUserSuspendedByAdmin(me.getAccountId(), userId, req.suspended());
        return ResponseEntity.noContent().build();
    }
}
