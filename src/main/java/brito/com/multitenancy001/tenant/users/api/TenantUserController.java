package brito.com.multitenancy001.tenant.users.api;

import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.validation.ValidationPatterns;
import brito.com.multitenancy001.tenant.users.api.dto.TenantUserCreateRequest;
import brito.com.multitenancy001.tenant.users.api.dto.TenantUserDetailsResponse;
import brito.com.multitenancy001.tenant.users.api.dto.TenantUserSummaryResponse;
import brito.com.multitenancy001.tenant.users.api.dto.TenantUsersListResponse;
import brito.com.multitenancy001.tenant.users.app.TenantUserService;
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

    private final TenantUserService tenantUserService;

    // ✅ Lista usuários do tenant (visão rica para TENANT_OWNER)
    @GetMapping
    @PreAuthorize("hasAuthority('TEN_USER_READ')")
    public ResponseEntity<TenantUsersListResponse> listTenantUsers() {
        return ResponseEntity.ok(tenantUserService.listTenantUsers());
    }

    // Lista usuários ativos do tenant.
    @GetMapping("/enabled")
    @PreAuthorize("hasAuthority('TEN_USER_READ')")
    public ResponseEntity<List<TenantUserSummaryResponse>> listEnabledTenantUsers() {
        return ResponseEntity.ok(tenantUserService.listEnabledTenantUsers());
    }

    // Busca detalhes de um usuário do tenant por id.
    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority('TEN_USER_READ')")
    public ResponseEntity<TenantUserDetailsResponse> getTenantUser(@PathVariable Long userId) {
        return ResponseEntity.ok(tenantUserService.getTenantUser(userId));
    }

    // Cria usuário no tenant.
    @PostMapping
    @PreAuthorize("hasAuthority('TEN_USER_CREATE')")
    public ResponseEntity<TenantUserDetailsResponse> createTenantUser(
            @Valid @RequestBody TenantUserCreateRequest tenantUserCreateRequest
    ) {
        TenantUserDetailsResponse response = tenantUserService.createTenantUser(tenantUserCreateRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Transfere a propriedade/admin (owner) do tenant para o usuário informado.
    @PatchMapping("/{userId}/transfer-admin")
    @PreAuthorize("hasAuthority('TEN_ROLE_TRANSFER')")
    public ResponseEntity<Void> transferTenantOwner(@PathVariable Long userId) {
        tenantUserService.transferTenantOwner(userId);
        return ResponseEntity.noContent().build();
    }

    // Atualiza status de suspensão do usuário
    @PatchMapping("/{userId}/status")
    @PreAuthorize("hasAuthority('TEN_USER_UPDATE')")
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
                tenantUserService.setTenantUserSuspendedByAdmin(userId, finalSuspended);

        return ResponseEntity.ok(response);
    }

    // Reseta a senha do usuário do tenant para um novo valor.
    @PatchMapping("/{userId}/password")
    @PreAuthorize("hasAuthority('TEN_USER_UPDATE')")
    public ResponseEntity<TenantUserSummaryResponse> resetTenantUserPassword(
            @PathVariable Long userId,
            @RequestParam
            @Pattern(
                    regexp = ValidationPatterns.PASSWORD_PATTERN,
                    message = "Senha fraca. Use pelo menos 8 caracteres com letras maiúsculas, minúsculas e números"
            )
            String newPassword
    ) {
        TenantUserSummaryResponse response = tenantUserService.resetTenantUserPassword(userId, newPassword);
        return ResponseEntity.ok(response);
    }

    // Soft-delete de usuário do tenant.
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAuthority('TEN_USER_DELETE')")
    public ResponseEntity<Void> deleteTenantUser(@PathVariable Long userId) {
        tenantUserService.softDeleteTenantUser(userId);
        return ResponseEntity.noContent().build();
    }

    // Hard-delete de usuário do tenant.
    @DeleteMapping("/{userId}/hard")
    @PreAuthorize("hasAuthority('TEN_USER_DELETE')")
    public ResponseEntity<Void> hardDeleteTenantUser(@PathVariable Long userId) {
        tenantUserService.hardDeleteTenantUser(userId);
        return ResponseEntity.noContent().build();
    }

    // Restaura usuário previamente deletado (soft-delete).
    @PatchMapping("/{userId}/restore")
    @PreAuthorize("hasAuthority('TEN_USER_RESTORE')")
    public ResponseEntity<TenantUserSummaryResponse> restoreTenantUser(@PathVariable Long userId) {
        TenantUserSummaryResponse response = tenantUserService.restoreTenantUser(userId);
        return ResponseEntity.ok(response);
    }

    // Busca detalhes de um usuário habilitado (enabled).
    @GetMapping("/enabled/{userId}")
    @PreAuthorize("hasAuthority('TEN_USER_READ')")
    public ResponseEntity<TenantUserDetailsResponse> getEnabledTenantUser(@PathVariable Long userId) {
        return ResponseEntity.ok(tenantUserService.getEnabledTenantUser(userId));
    }

    // Conta usuários habilitados (enabled) do tenant.
    @GetMapping("/enabled/count")
    @PreAuthorize("hasAuthority('TEN_USER_READ')")
    public ResponseEntity<Long> countEnabledTenantUsers() {
        return ResponseEntity.ok(tenantUserService.countEnabledTenantUsers());
    }
}
