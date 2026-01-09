package brito.com.multitenancy001.tenant.api.controller.users;

import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountUserSummaryResponse;
import brito.com.multitenancy001.shared.validation.ValidationPatterns;
import brito.com.multitenancy001.tenant.api.dto.users.TenantUserCreateRequest;
import brito.com.multitenancy001.tenant.api.dto.users.TenantUserDetailsResponse;
import brito.com.multitenancy001.tenant.application.TenantUserService;
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
    
    @PostMapping
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<TenantUserDetailsResponse> createTenantUser(
            @Valid @RequestBody TenantUserCreateRequest request) {
    	TenantUserDetailsResponse response = tenantUserService.createTenantUser(request);
                                               
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @GetMapping
    @PreAuthorize("hasAnyRole( 'TENANT_ADMIN', 'PRODUCT_MANAGER', 'SALES_MANAGER', 'VIEWER')")
    public ResponseEntity<List<AccountUserSummaryResponse>> listTenantUsers() {
        List<AccountUserSummaryResponse> users = tenantUserService.listTenantUsers();
        return ResponseEntity.ok(users);
    }
    
    @GetMapping("/active")
    @PreAuthorize("hasAnyRole( 'TENANT_ADMIN', 'PRODUCT_MANAGER', 'SALES_MANAGER')")
    public ResponseEntity<List<AccountUserSummaryResponse>> listActiveTenantUsers() {
        List<AccountUserSummaryResponse> users = tenantUserService.listActiveTenantUsers();
        return ResponseEntity.ok(users);
    }
    
    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole( 'TENANT_ADMIN', 'PRODUCT_MANAGER', 'SALES_MANAGER')")
    public ResponseEntity<TenantUserDetailsResponse> getTenantUser(@PathVariable Long userId) {
    	TenantUserDetailsResponse user = tenantUserService.getTenantUser(userId);
        return ResponseEntity.ok(user);
    }
    
    @PatchMapping("/{userId}/status")
    @PreAuthorize("hasAnyRole( 'TENANT_ADMIN')")
    public ResponseEntity<AccountUserSummaryResponse> updateTenantUserStatus(
            @PathVariable Long userId,
            @RequestParam boolean active) {
    	AccountUserSummaryResponse response = tenantUserService.updateTenantUserStatus(userId, active);
        return ResponseEntity.ok(response);
    }
    
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAnyRole( 'TENANT_ADMIN')")
    public ResponseEntity<Void> deleteTenantUser(@PathVariable Long userId) {
        tenantUserService.softDeleteTenantUser(userId);
        return ResponseEntity.noContent().build();
    }
    
    @PatchMapping("/{userId}/restore")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN')")
    public ResponseEntity<AccountUserSummaryResponse> restoreTenantUser(@PathVariable Long userId) {
    	AccountUserSummaryResponse response = tenantUserService.restoreTenantUser(userId);
        return ResponseEntity.ok(response);
    }
    
    @PatchMapping("/{userId}/password")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN')")
    public ResponseEntity<AccountUserSummaryResponse> resetTenantUserPassword(
            @PathVariable Long userId,
            @RequestParam
            @Pattern(regexp = ValidationPatterns.PASSWORD_PATTERN,
                 message = "Senha fraca. Use pelo menos 8 caracteres com letras maiúsculas, minúsculas e números")
           String newPassword) {
    	AccountUserSummaryResponse response = tenantUserService.resetTenantUserPassword(userId, newPassword);
        return ResponseEntity.ok(response);
    }
    
    @DeleteMapping("/{userId}/hard")
    @PreAuthorize("hasRole('TENANT_ADMIN')")
    public ResponseEntity<Void> hardDeleteTenantUser(@PathVariable Long userId) {
        tenantUserService.hardDeleteTenantUser(userId);
        return ResponseEntity.noContent().build();
    }
}