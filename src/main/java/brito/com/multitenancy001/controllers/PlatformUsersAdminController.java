package brito.com.multitenancy001.controllers;

import brito.com.multitenancy001.dtos.UserCreateRequest;
import brito.com.multitenancy001.dtos.UserResponse;
import brito.com.multitenancy001.services.PlatformUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/platform-users")
@RequiredArgsConstructor
public class PlatformUsersAdminController {

    private final PlatformUserService platformUserService;

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SUPPORT')")
    public ResponseEntity<UserResponse> createPlatformUser(@Valid @RequestBody UserCreateRequest request) {
        UserResponse response = platformUserService.createPlatformUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SUPPORT','STAFF')")
    public ResponseEntity<List<UserResponse>> listPlatformUsers() {
        return ResponseEntity.ok(platformUserService.listPlatformUsers());
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SUPPORT','STAFF')")
    public ResponseEntity<UserResponse> getPlatformUser(@PathVariable Long userId) {
        return ResponseEntity.ok(platformUserService.getPlatformUser(userId));
    }

    @PatchMapping("/{userId}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SUPPORT')")
    public ResponseEntity<UserResponse> updatePlatformUserStatus(
            @PathVariable Long userId,
            @RequestParam boolean active) {
        return ResponseEntity.ok(platformUserService.updatePlatformUserStatus(userId, active));
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> deletePlatformUser(@PathVariable Long userId) {
        platformUserService.softDeletePlatformUser(userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{userId}/restore")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<UserResponse> restorePlatformUser(@PathVariable Long userId) {
        return ResponseEntity.ok(platformUserService.restorePlatformUser(userId));
    }
}
