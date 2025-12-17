

package brito.com.multitenancy001.controllers;

import brito.com.multitenancy001.dtos.UserCreateRequest;
import brito.com.multitenancy001.dtos.UserResponse;
import brito.com.multitenancy001.services.UserAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/account-users")
@RequiredArgsConstructor
public class UserAccountController {
    
    private final UserAccountService accountUserService;
    
    @PostMapping("/{accountId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<UserResponse> createAccountUser(
            @PathVariable Long accountId,
            @Valid @RequestBody UserCreateRequest request) {
        UserResponse response = accountUserService.createAccountUser(accountId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    
    @GetMapping("/{accountId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<List<UserResponse>> listAccountUsersByAccount(
            @PathVariable Long accountId) {
        List<UserResponse> users = accountUserService.listAccountUsersByAccount(accountId);
        return ResponseEntity.ok(users);
    }
    
    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<UserResponse> getAccountUser(@PathVariable Long userId) {
        UserResponse user = accountUserService.getAccountUser(userId);
        return ResponseEntity.ok(user);
    }
    
    @PatchMapping("/{userId}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<UserResponse> updateAccountUserStatus(
            @PathVariable Long userId,
            @RequestParam boolean active) {
        UserResponse response = accountUserService.updateAccountUserStatus(userId, active);
        return ResponseEntity.ok(response);
    }
    
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> deleteAccountUser(@PathVariable Long userId) {
        accountUserService.softDeleteAccountUser(userId);
        return ResponseEntity.noContent().build();
    }
    
    @PatchMapping("/{userId}/restore")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<UserResponse> restoreAccountUser(@PathVariable Long userId) {
        UserResponse response = accountUserService.restoreAccountUser(userId);
        return ResponseEntity.ok(response);
    }
}