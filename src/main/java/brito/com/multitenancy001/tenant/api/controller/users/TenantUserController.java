package brito.com.multitenancy001.tenant.api.controller.users;

import brito.com.multitenancy001.shared.validation.ValidationPatterns;
import brito.com.multitenancy001.tenant.api.dto.users.TenantUserCreateRequest;
import brito.com.multitenancy001.tenant.api.dto.users.TenantUserDetailsResponse;
import brito.com.multitenancy001.tenant.api.dto.users.TenantUserSummaryResponse;
import brito.com.multitenancy001.tenant.application.user.TenantUserService;
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

    @PatchMapping("/{userId}/transfer-admin")
    @PreAuthorize("hasAuthority('TEN_ROLE_TRANSFER')")
    public ResponseEntity<Void> transferTenantOwner(@PathVariable Long userId) {
        tenantUserService.transferTenantOwner(userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('TEN_USER_CREATE')")
    public ResponseEntity<TenantUserDetailsResponse> createTenantUser(@Valid @RequestBody TenantUserCreateRequest tenantUserCreateRequest) {
        TenantUserDetailsResponse response = tenantUserService.createTenantUser(tenantUserCreateRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('TEN_USER_READ')")
    public ResponseEntity<List<TenantUserSummaryResponse>> listTenantUsers() {
        List<TenantUserSummaryResponse> users = tenantUserService.listTenantUsers();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/active")
    @PreAuthorize("hasAuthority('TEN_USER_READ')")
    public ResponseEntity<List<TenantUserSummaryResponse>> listActiveTenantUsers() {
        List<TenantUserSummaryResponse> users = tenantUserService.listActiveTenantUsers();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority('TEN_USER_READ')")
    public ResponseEntity<TenantUserDetailsResponse> getTenantUser(@PathVariable Long userId) {
        TenantUserDetailsResponse user = tenantUserService.getTenantUser(userId);
        return ResponseEntity.ok(user);
    }

    @PatchMapping("/{userId}/status")
    @PreAuthorize("hasAuthority('TEN_USER_UPDATE')")
    public ResponseEntity<TenantUserSummaryResponse> updateTenantUserStatus(
            @PathVariable Long userId,
            @RequestParam(required = false) Boolean suspended,
            @RequestParam(required = false) Boolean active
    ) {
        // compat: se cliente antigo mandar active, converte
        boolean finalSuspended = (suspended != null)
                ? suspended
                : (active != null && !active);

        TenantUserSummaryResponse response =
                tenantUserService.setTenantUserSuspendedByAdmin(userId, finalSuspended);

        return ResponseEntity.ok(response);
    }


    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAuthority('TEN_USER_DELETE')")
    public ResponseEntity<Void> deleteTenantUser(@PathVariable Long userId) {
        tenantUserService.softDeleteTenantUser(userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{userId}/restore")
    @PreAuthorize("hasAuthority('TEN_USER_RESTORE')")
    public ResponseEntity<TenantUserSummaryResponse> restoreTenantUser(@PathVariable Long userId) {
        TenantUserSummaryResponse response = tenantUserService.restoreTenantUser(userId);
        return ResponseEntity.ok(response);
    }

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

    @DeleteMapping("/{userId}/hard")
    @PreAuthorize("hasAuthority('TEN_USER_DELETE')")
    public ResponseEntity<Void> hardDeleteTenantUser(@PathVariable Long userId) {
        tenantUserService.hardDeleteTenantUser(userId);
        return ResponseEntity.noContent().build();
    }
}
