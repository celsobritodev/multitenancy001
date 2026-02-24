// src/main/java/brito/com/multitenancy001/controlplane/users/api/ControlPlaneUserController.java
package brito.com.multitenancy001.controlplane.users.api;

import brito.com.multitenancy001.controlplane.users.api.dto.*;
import brito.com.multitenancy001.controlplane.users.app.ControlPlaneUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/controlplane/users")  // ← ALTERADO: /api/admin/controlplane-users → /api/controlplane/users
@RequiredArgsConstructor
public class ControlPlaneUserController {

    private final ControlPlaneUserService controlPlaneUserService;

    @PostMapping
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_USER_WRITE.asAuthority())")
    public ResponseEntity<ControlPlaneUserDetailsResponse> createControlPlaneUser(@Valid @RequestBody ControlPlaneUserCreateRequest request) {
        ControlPlaneUserDetailsResponse response = controlPlaneUserService.createControlPlaneUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_USER_READ.asAuthority())")
    public ResponseEntity<List<ControlPlaneUserDetailsResponse>> listControlPlaneUsers() {
        return ResponseEntity.ok(controlPlaneUserService.listControlPlaneUsers());
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_USER_READ.asAuthority())")
    public ResponseEntity<ControlPlaneUserDetailsResponse> getControlPlaneUser(@PathVariable Long userId) {
        return ResponseEntity.ok(controlPlaneUserService.getControlPlaneUser(userId));
    }

    @PatchMapping("/{userId}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_USER_WRITE.asAuthority())")
    public ResponseEntity<ControlPlaneUserDetailsResponse> updateControlPlaneUser(
            @PathVariable Long userId,
            @Valid @RequestBody ControlPlaneUserUpdateRequest request
    ) {
        return ResponseEntity.ok(controlPlaneUserService.updateControlPlaneUser(userId, request));
    }

    @PatchMapping("/{userId}/permissions")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_USER_WRITE.asAuthority())")
    public ResponseEntity<ControlPlaneUserDetailsResponse> updateControlPlaneUserPermissions(
            @PathVariable Long userId,
            @Valid @RequestBody ControlPlaneUserPermissionsUpdateRequest request
    ) {
        return ResponseEntity.ok(controlPlaneUserService.updateControlPlaneUserPermissions(userId, request));
    }

    @PatchMapping("/{userId}/reset-password")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_USER_PASSWORD_RESET.asAuthority())")
    public ResponseEntity<Void> resetPassword(
            @PathVariable Long userId,
            @Valid @RequestBody ControlPlaneUserPasswordResetRequest request
    ) {
        controlPlaneUserService.resetControlPlaneUserPassword(userId, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_USER_DELETE.asAuthority())")
    public ResponseEntity<Void> deleteControlPlaneUser(@PathVariable Long userId) {
        controlPlaneUserService.softDeleteControlPlaneUser(userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{userId}/restore")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_USER_WRITE.asAuthority())")
    public ResponseEntity<ControlPlaneUserDetailsResponse> restoreControlPlaneUser(@PathVariable Long userId) {
        return ResponseEntity.ok(controlPlaneUserService.restoreControlPlaneUser(userId));
    }

    // =========================================================
    // SUSPEND / RESTORE (ADMIN)
    // =========================================================

    @PostMapping("/{userId}/suspend-by-admin")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_USER_WRITE.asAuthority())")
    public ResponseEntity<Void> suspendByAdmin(
            @PathVariable Long userId,
            @Valid @RequestBody(required = false) ControlPlaneUserSuspendRequest request
    ) {
        controlPlaneUserService.suspendControlPlaneUserByAdmin(userId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{userId}/restore-by-admin")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_USER_WRITE.asAuthority())")
    public ResponseEntity<Void> restoreByAdmin(
            @PathVariable Long userId,
            @Valid @RequestBody(required = false) ControlPlaneUserSuspendRequest request
    ) {
        controlPlaneUserService.restoreControlPlaneUserByAdmin(userId, request);
        return ResponseEntity.noContent().build();
    }

    // =========================================================
    // ENABLED
    // =========================================================

    @GetMapping("/enabled")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_USER_READ.asAuthority())")
    public ResponseEntity<List<ControlPlaneUserDetailsResponse>> listEnabled() {
        return ResponseEntity.ok(controlPlaneUserService.listEnabledControlPlaneUsers());
    }

    @GetMapping("/{userId}/enabled")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_USER_READ.asAuthority())")
    public ResponseEntity<ControlPlaneUserDetailsResponse> getEnabled(@PathVariable Long userId) {
        return ResponseEntity.ok(controlPlaneUserService.getEnabledControlPlaneUser(userId));
    }
}