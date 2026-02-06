package brito.com.multitenancy001.controlplane.users.api.admin;

import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserCreateRequest;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserDetailsResponse;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserPasswordResetRequest;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserPermissionsUpdateRequest;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserUpdateRequest;
import brito.com.multitenancy001.controlplane.users.app.ControlPlaneUserService;
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

    // Cria um novo usuário do Control Plane (Admin)
    @PostMapping
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_USER_WRITE.name())")
    public ResponseEntity<ControlPlaneUserDetailsResponse> createControlPlaneUser(
            @Valid @RequestBody ControlPlaneUserCreateRequest request
    ) {
        ControlPlaneUserDetailsResponse response = controlPlaneUserService.createControlPlaneUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Lista todos os usuários do Control Plane (Admin)
    @GetMapping
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_USER_READ.name())")
    public ResponseEntity<List<ControlPlaneUserDetailsResponse>> listControlPlaneUsers() {
        return ResponseEntity.ok(controlPlaneUserService.listControlPlaneUsers());
    }

    // Obtém um usuário do Control Plane (Admin) pelo id
    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_USER_READ.name())")
    public ResponseEntity<ControlPlaneUserDetailsResponse> getControlPlaneUser(@PathVariable Long userId) {
        return ResponseEntity.ok(controlPlaneUserService.getControlPlaneUser(userId));
    }

    // Atualiza dados do usuário do Control Plane (Admin)
    // Regra 2 (BUILT_IN): se tentar alterar, o service responde 409 USER_BUILT_IN_IMMUTABLE
    @PatchMapping("/{userId}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_USER_WRITE.name())")
    public ResponseEntity<ControlPlaneUserDetailsResponse> updateControlPlaneUser(
            @PathVariable Long userId,
            @Valid @RequestBody ControlPlaneUserUpdateRequest request
    ) {
        return ResponseEntity.ok(controlPlaneUserService.updateControlPlaneUser(userId, request));
    }

    // Atualiza apenas as permissões explícitas (override) do usuário do Control Plane (Admin)
    // Regra 2 (BUILT_IN): se tentar alterar permissões, 409 USER_BUILT_IN_IMMUTABLE
    @PatchMapping("/{userId}/permissions")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_USER_WRITE.name())")
    public ResponseEntity<ControlPlaneUserDetailsResponse> updateControlPlaneUserPermissions(
            @PathVariable Long userId,
            @Valid @RequestBody ControlPlaneUserPermissionsUpdateRequest request
    ) {
        return ResponseEntity.ok(controlPlaneUserService.updateControlPlaneUserPermissions(userId, request));
    }

    // Reseta/define uma nova senha para o usuário do Control Plane (Admin)
    // ✅ Permitido para BUILT_IN (regra 2 permite trocar senha)
    @PatchMapping("/{userId}/reset-password")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_USER_PASSWORD_RESET.name())")
    public ResponseEntity<Void> resetPassword(
            @PathVariable Long userId,
            @Valid @RequestBody ControlPlaneUserPasswordResetRequest request
    ) {
        controlPlaneUserService.resetControlPlaneUserPassword(userId, request);
        return ResponseEntity.noContent().build();
    }

    // Soft delete do usuário do Control Plane (Admin)
    // Regra 2 (BUILT_IN): se tentar deletar, 409 USER_BUILT_IN_IMMUTABLE
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_USER_DELETE.name())")
    public ResponseEntity<Void> deleteControlPlaneUser(@PathVariable Long userId) {
        controlPlaneUserService.softDeleteControlPlaneUser(userId);
        return ResponseEntity.noContent().build();
    }

    // Restaura (undelete) um usuário do Control Plane (Admin)
    // Regra 2 (BUILT_IN): se tentar restaurar, 409 USER_BUILT_IN_IMMUTABLE
    @PatchMapping("/{userId}/restore")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_USER_WRITE.name())")
    public ResponseEntity<ControlPlaneUserDetailsResponse> restoreControlPlaneUser(@PathVariable Long userId) {
        return ResponseEntity.ok(controlPlaneUserService.restoreControlPlaneUser(userId));
    }

    // Lista apenas usuários habilitados (enabled)
    @GetMapping("/enabled")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_USER_READ.name())")
    public ResponseEntity<List<ControlPlaneUserDetailsResponse>> listEnabled() {
        return ResponseEntity.ok(controlPlaneUserService.listEnabledControlPlaneUsers());
    }

    // Obtém usuário habilitado (enabled) pelo id
    @GetMapping("/{userId}/enabled")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_USER_READ.name())")
    public ResponseEntity<ControlPlaneUserDetailsResponse> getEnabled(@PathVariable Long userId) {
        return ResponseEntity.ok(controlPlaneUserService.getEnabledControlPlaneUser(userId));
    }
}
