package brito.com.multitenancy001.tenant.users.api;

import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.validation.ValidationPatterns;
import brito.com.multitenancy001.tenant.users.api.dto.TenantUserCreateRequest;
import brito.com.multitenancy001.tenant.users.api.dto.TenantUserDetailsResponse;
import brito.com.multitenancy001.tenant.users.api.dto.TenantUserSummaryResponse;
import brito.com.multitenancy001.tenant.users.api.dto.TenantUsersListResponse;
import brito.com.multitenancy001.tenant.users.app.TenantUserFacade;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tenant/users")
@RequiredArgsConstructor
public class TenantUserController {

    private final TenantUserFacade tenantUserFacade;

    // ✅ Lista usuários do tenant (visão rica para TENANT_OWNER)
    @GetMapping
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_USER_READ.name())")
    public ResponseEntity<TenantUsersListResponse> listTenantUsers() {
        return ResponseEntity.ok(tenantUserFacade.listTenantUsers());
    }

    // Lista usuários ativos do tenant.
    @GetMapping("/enabled")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_USER_READ.name())")
    public ResponseEntity<List<TenantUserSummaryResponse>> listEnabledTenantUsers() {
        return ResponseEntity.ok(tenantUserFacade.listEnabledTenantUsers());
    }

    // Busca detalhes de um usuário do tenant por id.
    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_USER_READ.name())")
    public ResponseEntity<TenantUserDetailsResponse> getTenantUser(@PathVariable Long userId) {
        return ResponseEntity.ok(tenantUserFacade.getTenantUser(userId));
    }

    // Cria usuário no tenant.
    @PostMapping
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_USER_CREATE.name())")
    public ResponseEntity<TenantUserDetailsResponse> createTenantUser(
            @Valid @RequestBody TenantUserCreateRequest tenantUserCreateRequest
    ) {
        TenantUserDetailsResponse response = tenantUserFacade.createTenantUser(tenantUserCreateRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Transfere a propriedade/admin (owner) do tenant para o usuário informado.
    @PatchMapping("/{userId}/transfer-admin")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_ROLE_TRANSFER.name())")
    public ResponseEntity<Void> transferTenantOwner(@PathVariable Long userId) {
        tenantUserFacade.transferTenantOwner(userId);
        return ResponseEntity.noContent().build();
    }

    // Atualiza status de suspensão do usuário
    @PatchMapping("/{userId}/status")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_USER_UPDATE.name())")
    public ResponseEntity<TenantUserSummaryResponse> updateTenantUserStatus(
            @PathVariable Long userId,
            @RequestParam(required = false) Boolean suspendedByAccount,
            @RequestParam(required = false) Boolean suspendedByAdmin
    ) {
        if (suspendedByAccount == null && suspendedByAdmin == null) {
            throw new ApiException("INVALID_STATUS", "Informe 'suspended' ou 'enabled' ", 400);
        }

        boolean finalSuspended = (suspendedByAccount != null) ? suspendedByAccount : !suspendedByAdmin;

        TenantUserSummaryResponse response =
                tenantUserFacade.setTenantUserSuspendedByAdmin(userId, finalSuspended);

        return ResponseEntity.ok(response);
    }

    // Reseta a senha do usuário do tenant para um novo valor.
    @PatchMapping("/{userId}/password")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_USER_UPDATE.name())")
    public ResponseEntity<TenantUserSummaryResponse> resetTenantUserPassword(
            @PathVariable Long userId,
            @RequestParam
            @Pattern(
                    regexp = ValidationPatterns.PASSWORD_PATTERN,
                    message = "Senha fraca. Use pelo menos 8 caracteres com letras maiúsculas, minúsculas e números"
            )
            String newPassword
    ) {
        TenantUserSummaryResponse response = tenantUserFacade.resetTenantUserPassword(userId, newPassword);
        return ResponseEntity.ok(response);
    }

    // Soft-delete de usuário do tenant.
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_USER_DELETE.name())")
    public ResponseEntity<Void> deleteTenantUser(@PathVariable Long userId) {
        tenantUserFacade.softDeleteTenantUser(userId);
        return ResponseEntity.noContent().build();
    }

    // Hard-delete de usuário do tenant.
    @DeleteMapping("/{userId}/hard")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_USER_DELETE.name())")
    public ResponseEntity<Void> hardDeleteTenantUser(@PathVariable Long userId) {
        tenantUserFacade.hardDeleteTenantUser(userId);
        return ResponseEntity.noContent().build();
    }

    // Restaura usuário previamente deletado (soft-delete).
    @PatchMapping("/{userId}/restore")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_USER_RESTORE.name())")
    public ResponseEntity<TenantUserSummaryResponse> restoreTenantUser(@PathVariable Long userId) {
        TenantUserSummaryResponse response = tenantUserFacade.restoreTenantUser(userId);
        return ResponseEntity.ok(response);
    }

    // Busca detalhes de um usuário habilitado (enabled).
    @GetMapping("/enabled/{userId}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_USER_READ.name())")
    public ResponseEntity<TenantUserDetailsResponse> getEnabledTenantUser(@PathVariable Long userId) {
        return ResponseEntity.ok(tenantUserFacade.getEnabledTenantUser(userId));
    }

    // Conta usuários habilitados (enabled) do tenant.
    @GetMapping("/enabled/count")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_USER_READ.name())")
    public ResponseEntity<Long> countEnabledTenantUsers() {
        return ResponseEntity.ok(tenantUserFacade.countEnabledTenantUsers());
    }
}
