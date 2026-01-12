package brito.com.multitenancy001.tenant.api.controller.users.admin;

import brito.com.multitenancy001.tenant.api.dto.users.admin.TenantUserAdminSuspendRequest;
import brito.com.multitenancy001.tenant.application.user.admin.TenantUserAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tenant/admin/users")
@RequiredArgsConstructor
public class TenantUserAdminController {

    private final TenantUserAdminService tenantUserAdminService;

    @PatchMapping("/{userId}/suspend")
    @PreAuthorize("hasAuthority('TEN_USER_SUSPEND')")
    public ResponseEntity<Void> suspendUser(
            @PathVariable Long userId,
            @RequestBody TenantUserAdminSuspendRequest tenantUserAdminSuspendRequest
    ) {
        tenantUserAdminService.setUserSuspendedByAdmin(userId, tenantUserAdminSuspendRequest.suspended());
        return ResponseEntity.noContent().build();
    }
}
