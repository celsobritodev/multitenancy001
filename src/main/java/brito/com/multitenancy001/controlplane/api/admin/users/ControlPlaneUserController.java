package brito.com.multitenancy001.controlplane.api.admin.users;

import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneUserCreateRequest;
import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneUserDetailsResponse;
import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneUserPermissionsUpdateRequest;
import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneUserUpdateRequest;
import brito.com.multitenancy001.controlplane.application.user.ControlPlaneUserService;
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
public class ControlPlaneUserController {

    private final ControlPlaneUserService controlPlaneUserService;

    @PostMapping
    @PreAuthorize("hasAuthority('CP_USER_WRITE')")
    public ResponseEntity<ControlPlaneUserDetailsResponse> createControlPlaneUser(
            @Valid @RequestBody ControlPlaneUserCreateRequest request
    ) {
        ControlPlaneUserDetailsResponse response = controlPlaneUserService.createControlPlaneUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CP_USER_READ')")
    public ResponseEntity<List<ControlPlaneUserDetailsResponse>> listControlPlaneUsers() {
        return ResponseEntity.ok(controlPlaneUserService.listControlPlaneUsers());
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority('CP_USER_READ')")
    public ResponseEntity<ControlPlaneUserDetailsResponse> getControlPlaneUser(@PathVariable Long userId) {
        return ResponseEntity.ok(controlPlaneUserService.getControlPlaneUser(userId));
    }

    /**
     * ✅ Update geral:
     * - name/email/role/username/permissions (se você decidir aceitar tudo aqui)
     * - Service faz as barreiras:
     *   - bloqueia update se systemUser=true
     *   - bloqueia rename para username reservado
     *   - bloqueia alterar permissions se não for OWNER (e bloqueia se systemUser=true)
     */
    @PatchMapping("/{userId}")
    @PreAuthorize("hasAuthority('CP_USER_WRITE')")
    public ResponseEntity<ControlPlaneUserDetailsResponse> updateControlPlaneUser(
            @PathVariable Long userId,
            @Valid @RequestBody ControlPlaneUserUpdateRequest request
    ) {
        return ResponseEntity.ok(controlPlaneUserService.updateControlPlaneUser(userId, request));
    }

    @PatchMapping("/{userId}/status")
    @PreAuthorize("hasAuthority('CP_USER_WRITE')")
    public ResponseEntity<ControlPlaneUserDetailsResponse> updateControlPlaneUserStatus(
            @PathVariable Long userId,
            @RequestParam boolean active
    ) {
        return ResponseEntity.ok(controlPlaneUserService.updateControlPlaneUserStatus(userId, active));
    }

    /**
     * ✅ Endpoint dedicado para permissions:
     * - Service faz as barreiras:
     *   - bloqueia se systemUser=true
     *   - só OWNER pode
     */
    @PatchMapping("/{userId}/permissions")
    @PreAuthorize("hasAuthority('CP_USER_WRITE')")
    public ResponseEntity<ControlPlaneUserDetailsResponse> updateControlPlaneUserPermissions(
            @PathVariable Long userId,
            @Valid @RequestBody ControlPlaneUserPermissionsUpdateRequest request
    ) {
        return ResponseEntity.ok(controlPlaneUserService.updateControlPlaneUserPermissions(userId, request));
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAuthority('CP_USER_DELETE')")
    public ResponseEntity<Void> deleteControlPlaneUser(@PathVariable Long userId) {
        controlPlaneUserService.softDeleteControlPlaneUser(userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{userId}/restore")
    @PreAuthorize("hasAuthority('CP_USER_WRITE')")
    public ResponseEntity<ControlPlaneUserDetailsResponse> restoreControlPlaneUser(@PathVariable Long userId) {
        return ResponseEntity.ok(controlPlaneUserService.restoreControlPlaneUser(userId));
    }
    
    
    @PatchMapping("/{userId}/reset-password")
    @PreAuthorize("hasAuthority('CP_USER_PASSWORD_RESET')")
    public ResponseEntity<Void> resetPassword(
            @PathVariable Long userId,
            @Valid @RequestBody brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneUserPasswordResetRequest request
    ) {
        controlPlaneUserService.resetControlPlaneUserPassword(userId, request);
        return ResponseEntity.noContent().build();
    }


}
