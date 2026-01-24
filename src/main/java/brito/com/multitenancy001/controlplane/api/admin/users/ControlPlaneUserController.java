package brito.com.multitenancy001.controlplane.api.admin.users;

import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneUserCreateRequest;
import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneUserDetailsResponse;
import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneUserPasswordResetRequest;
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

    // Cria um usuário do Control Plane (plataforma) respeitando as regras de role/permissions do criador.
    @PostMapping
    @PreAuthorize("hasAuthority('CP_USER_WRITE')")
    public ResponseEntity<ControlPlaneUserDetailsResponse> createControlPlaneUser(
            @Valid @RequestBody ControlPlaneUserCreateRequest request
    ) {
        ControlPlaneUserDetailsResponse response = controlPlaneUserService.createControlPlaneUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Lista usuários habilitados do Control Plane.
    @GetMapping
    @PreAuthorize("hasAuthority('CP_USER_READ')")
    public ResponseEntity<List<ControlPlaneUserDetailsResponse>> listControlPlaneUsers() {
        return ResponseEntity.ok(controlPlaneUserService.listControlPlaneUsers());
    }

    // Busca um usuário ativo do Control Plane por ID.
    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority('CP_USER_READ')")
    public ResponseEntity<ControlPlaneUserDetailsResponse> getControlPlaneUser(@PathVariable Long userId) {
        return ResponseEntity.ok(controlPlaneUserService.getControlPlaneUser(userId));
    }

    // Atualiza dados do usuário (nome/email/username/role/permissions) conforme regras e barreiras do serviço.
    @PatchMapping("/{userId}")
    @PreAuthorize("hasAuthority('CP_USER_WRITE')")
    public ResponseEntity<ControlPlaneUserDetailsResponse> updateControlPlaneUser(
            @PathVariable Long userId,
            @Valid @RequestBody ControlPlaneUserUpdateRequest request
    ) {
        return ResponseEntity.ok(controlPlaneUserService.updateControlPlaneUser(userId, request));
    }

    // Atualiza o conjunto de permissions do usuário conforme regras e barreiras do serviço.
    @PatchMapping("/{userId}/permissions")
    @PreAuthorize("hasAuthority('CP_USER_WRITE')")
    public ResponseEntity<ControlPlaneUserDetailsResponse> updateControlPlaneUserPermissions(
            @PathVariable Long userId,
            @Valid @RequestBody ControlPlaneUserPermissionsUpdateRequest request
    ) {
        return ResponseEntity.ok(controlPlaneUserService.updateControlPlaneUserPermissions(userId, request));
    }

    // Reseta a senha do usuário alvo (ação administrativa), aplicando regras de segurança do serviço.
    @PatchMapping("/{userId}/reset-password")
    @PreAuthorize("hasAuthority('CP_USER_PASSWORD_RESET')")
    public ResponseEntity<Void> resetPassword(
            @PathVariable Long userId,
            @Valid @RequestBody ControlPlaneUserPasswordResetRequest request
    ) {
        controlPlaneUserService.resetControlPlaneUserPassword(userId, request);
        return ResponseEntity.noContent().build();
    }

    // Executa soft delete do usuário (ação administrativa) conforme regras do serviço.
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAuthority('CP_USER_DELETE')")
    public ResponseEntity<Void> deleteControlPlaneUser(@PathVariable Long userId) {
        controlPlaneUserService.softDeleteControlPlaneUser(userId);
        return ResponseEntity.noContent().build();
    }

    // Restaura um usuário previamente deletado (ação administrativa) conforme regras do serviço.
    @PatchMapping("/{userId}/restore")
    @PreAuthorize("hasAuthority('CP_USER_WRITE')")
    public ResponseEntity<ControlPlaneUserDetailsResponse> restoreControlPlaneUser(@PathVariable Long userId) {
        return ResponseEntity.ok(controlPlaneUserService.restoreControlPlaneUser(userId));
    }
    
    // Lista usuários habilitados (enabled = not deleted + not suspended).
    @GetMapping("/enabled")
    @PreAuthorize("hasAuthority('CP_USER_READ')")
    public ResponseEntity<List<ControlPlaneUserDetailsResponse>> listEnabled() {
        return ResponseEntity.ok(controlPlaneUserService.listEnabledControlPlaneUsers());
    }

    // Busca usuário habilitado por id.
    @GetMapping("/{userId}/enabled")
    @PreAuthorize("hasAuthority('CP_USER_READ')")
    public ResponseEntity<ControlPlaneUserDetailsResponse> getEnabled(@PathVariable Long userId) {
        return ResponseEntity.ok(controlPlaneUserService.getEnabledControlPlaneUser(userId));
    }

}
