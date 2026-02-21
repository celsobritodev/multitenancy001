package brito.com.multitenancy001.tenant.users.api.admin;

import brito.com.multitenancy001.tenant.users.api.dto.admin.TenantUserAdminSuspendRequest;
import brito.com.multitenancy001.tenant.users.app.admin.TenantUserAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Controller ADMIN (Tenant) para ações operacionais sobre usuários.
 *
 * Regras:
 * - Controller NÃO acessa repository.
 * - Payload deve ser validado com @Valid (ControllerComplianceVerifier).
 * - Controller delega para Application Service.
 */
@RestController
@RequestMapping("/api/tenant/admin/users")
@RequiredArgsConstructor
public class TenantUserAdminController {

    private final TenantUserAdminService tenantUserAdminService;

    /**
     * Suspende ou reativa usuário do tenant por ação administrativa.
     */
    @PatchMapping("/{userId}/suspend")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_USER_SUSPEND.asAuthority())")
    public ResponseEntity<Void> suspendUser(
            /* Identificador do usuário alvo. */
            @PathVariable Long userId,
            /* Valida o payload (@Valid é obrigatório pelo compliance). */
            @Valid @RequestBody TenantUserAdminSuspendRequest tenantUserAdminSuspendRequest
    ) {
        tenantUserAdminService.setUserSuspendedByAdmin(userId, tenantUserAdminSuspendRequest.suspended());
        return ResponseEntity.noContent().build();
    }
}