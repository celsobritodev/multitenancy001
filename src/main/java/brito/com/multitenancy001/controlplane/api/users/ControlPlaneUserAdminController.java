package brito.com.multitenancy001.controlplane.api.users;

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
@RequestMapping("/api/admin/controlplane-users")
@RequiredArgsConstructor
public class ControlPlaneUserAdminController {

    private final ControlPlaneUserService controlPlaneUserService;

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SUPPORT')")
    public ResponseEntity<ControlPlaneUserDetailsResponse> createControlPlaneUser(@Valid @RequestBody ControlPlaneUserCreateRequest request) {
    	ControlPlaneUserDetailsResponse response = controlPlaneUserService.createControlPlaneUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SUPPORT','STAFF')")
    public ResponseEntity<List<ControlPlaneUserDetailsResponse>> listControlPlaneUsers() {
        return ResponseEntity.ok(controlPlaneUserService.listControlPlaneUsers());
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SUPPORT','STAFF')")
    public ResponseEntity<ControlPlaneUserDetailsResponse> getControlPlaneUser(@PathVariable Long userId) {
        return ResponseEntity.ok(controlPlaneUserService.getControlPlaneUser(userId));
    }

    @PatchMapping("/{userId}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SUPPORT')")
    public ResponseEntity<ControlPlaneUserDetailsResponse> updateControlPlaneUserStatus(
            @PathVariable Long userId,
            @RequestParam boolean active) {
        return ResponseEntity.ok(controlPlaneUserService.updateControlPlaneUserStatus(userId, active));
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> deleteControlPlaneUser(@PathVariable Long userId) {
        controlPlaneUserService.softDeleteControlPlaneUser(userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{userId}/restore")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ControlPlaneUserDetailsResponse> restoreControlPlaneUser(@PathVariable Long userId) {
        return ResponseEntity.ok(controlPlaneUserService.restoreControlPlaneUser(userId));
    }
}
