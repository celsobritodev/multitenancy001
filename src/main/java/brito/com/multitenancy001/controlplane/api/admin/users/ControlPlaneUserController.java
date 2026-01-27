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

    // Cria um novo usuário do Control Plane (Admin), aplicando validações e regras de negócio (ex.: email único, role/permissões válidas).
    @PostMapping
    @PreAuthorize("hasAuthority('CP_USER_WRITE')")
    public ResponseEntity<ControlPlaneUserDetailsResponse> createControlPlaneUser(
            @Valid @RequestBody ControlPlaneUserCreateRequest request
    ) {
        ControlPlaneUserDetailsResponse response = controlPlaneUserService.createControlPlaneUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Lista todos os usuários do Control Plane (Admin), incluindo estados (suspensões/deleção) conforme regras do serviço.
    @GetMapping
    @PreAuthorize("hasAuthority('CP_USER_READ')")
    public ResponseEntity<List<ControlPlaneUserDetailsResponse>> listControlPlaneUsers() {
        return ResponseEntity.ok(controlPlaneUserService.listControlPlaneUsers());
    }

    // Obtém os detalhes de um usuário do Control Plane (Admin) pelo id (pode incluir usuário suspenso/deletado, conforme regras do serviço).
    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority('CP_USER_READ')")
    public ResponseEntity<ControlPlaneUserDetailsResponse> getControlPlaneUser(@PathVariable Long userId) {
        return ResponseEntity.ok(controlPlaneUserService.getControlPlaneUser(userId));
    }

    // Atualiza dados do usuário do Control Plane (Admin) pelo id (ex.: nome, email, role, flags e/ou campos permitidos), conforme validações do serviço.
    @PatchMapping("/{userId}")
    @PreAuthorize("hasAuthority('CP_USER_WRITE')")
    public ResponseEntity<ControlPlaneUserDetailsResponse> updateControlPlaneUser(
            @PathVariable Long userId,
            @Valid @RequestBody ControlPlaneUserUpdateRequest request
    ) {
        return ResponseEntity.ok(controlPlaneUserService.updateControlPlaneUser(userId, request));
    }

    // Atualiza o conjunto de permissões explícitas (overrides) do usuário do Control Plane (Admin), validando escopo e consistência (somente CP_*).
    @PatchMapping("/{userId}/permissions")
    @PreAuthorize("hasAuthority('CP_USER_WRITE')")
    public ResponseEntity<ControlPlaneUserDetailsResponse> updateControlPlaneUserPermissions(
            @PathVariable Long userId,
            @Valid @RequestBody ControlPlaneUserPermissionsUpdateRequest request
    ) {
        return ResponseEntity.ok(controlPlaneUserService.updateControlPlaneUserPermissions(userId, request));
    }

    // Reseta/define uma nova senha para o usuário do Control Plane (Admin) (ação administrativa), conforme política de senha e regras do serviço.
    @PatchMapping("/{userId}/reset-password")
    @PreAuthorize("hasAuthority('CP_USER_PASSWORD_RESET')")
    public ResponseEntity<Void> resetPassword(
            @PathVariable Long userId,
            @Valid @RequestBody ControlPlaneUserPasswordResetRequest request
    ) {
        controlPlaneUserService.resetControlPlaneUserPassword(userId, request);
        return ResponseEntity.noContent().build();
    }

    // Realiza soft delete do usuário do Control Plane (Admin) pelo id (marca como deleted, preservando histórico/auditoria).
    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAuthority('CP_USER_DELETE')")
    public ResponseEntity<Void> deleteControlPlaneUser(@PathVariable Long userId) {
        controlPlaneUserService.softDeleteControlPlaneUser(userId);
        return ResponseEntity.noContent().build();
    }

    // Restaura (undelete) um usuário do Control Plane (Admin) previamente soft-deletado, conforme regras do serviço.
    @PatchMapping("/{userId}/restore")
    @PreAuthorize("hasAuthority('CP_USER_WRITE')")
    public ResponseEntity<ControlPlaneUserDetailsResponse> restoreControlPlaneUser(@PathVariable Long userId) {
        return ResponseEntity.ok(controlPlaneUserService.restoreControlPlaneUser(userId));
    }

    // Lista apenas usuários "habilitados" para operação: não deletados e não suspensos (nem por admin nem por conta), conforme regra do serviço.
    @GetMapping("/enabled")
    @PreAuthorize("hasAuthority('CP_USER_READ')")
    public ResponseEntity<List<ControlPlaneUserDetailsResponse>> listEnabled() {
        return ResponseEntity.ok(controlPlaneUserService.listEnabledControlPlaneUsers());
    }

    // Obtém usuário "habilitado" pelo id: retorna apenas se estiver apto para operar (não deletado e não suspenso), conforme regra do serviço.
    @GetMapping("/{userId}/enabled")
    @PreAuthorize("hasAuthority('CP_USER_READ')")
    public ResponseEntity<ControlPlaneUserDetailsResponse> getEnabled(@PathVariable Long userId) {
        return ResponseEntity.ok(controlPlaneUserService.getEnabledControlPlaneUser(userId));
    }

}
