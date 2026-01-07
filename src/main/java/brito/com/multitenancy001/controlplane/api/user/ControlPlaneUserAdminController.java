package brito.com.multitenancy001.controlplane.api.user;

import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneUserCreateRequest;
import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneUserDetailsResponse;
import brito.com.multitenancy001.controlplane.user.application.ControlPlaneUserService;
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
public class ControlPlaneUserAdminController {

    private final ControlPlaneUserService platformUserService;

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SUPPORT')")
    public ResponseEntity<ControlPlaneUserDetailsResponse> createPlatformUser(@Valid @RequestBody ControlPlaneUserCreateRequest request) {
    	ControlPlaneUserDetailsResponse response = platformUserService.createPlatformUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SUPPORT','STAFF')")
    public ResponseEntity<List<ControlPlaneUserDetailsResponse>> listPlatformUsers() {
        return ResponseEntity.ok(platformUserService.listPlatformUsers());
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SUPPORT','STAFF')")
    public ResponseEntity<ControlPlaneUserDetailsResponse> getPlatformUser(@PathVariable Long userId) {
        return ResponseEntity.ok(platformUserService.getPlatformUser(userId));
    }

    @PatchMapping("/{userId}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SUPPORT')")
    public ResponseEntity<ControlPlaneUserDetailsResponse> updatePlatformUserStatus(
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
    public ResponseEntity<ControlPlaneUserDetailsResponse> restorePlatformUser(@PathVariable Long userId) {
        return ResponseEntity.ok(platformUserService.restorePlatformUser(userId));
    }
}
