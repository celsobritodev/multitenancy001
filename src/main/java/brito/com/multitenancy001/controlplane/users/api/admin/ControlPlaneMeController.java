package brito.com.multitenancy001.controlplane.users.api.admin;

import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneChangeMyPasswordRequest;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneMeResponse;
import brito.com.multitenancy001.controlplane.users.app.ControlPlaneUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/me")
@RequiredArgsConstructor
public class ControlPlaneMeController {

    private final ControlPlaneUserService controlPlaneUserService;

    // Retorna os dados do usuário autenticado (inclui authorities), mesmo quando mustChangePassword=true.
    @GetMapping
    @PreAuthorize("hasAuthority('CP_USER_READ')")
    public ResponseEntity<ControlPlaneMeResponse> me() {
        return ResponseEntity.ok(controlPlaneUserService.getMe());
    }

    // Permite que o usuário autenticado altere a própria senha.
    @PatchMapping("/password")
    @PreAuthorize("hasAuthority('CP_USER_READ')")
    public ResponseEntity<Void> changeMyPassword(@Valid @RequestBody ControlPlaneChangeMyPasswordRequest request) {
        controlPlaneUserService.changeMyPassword(request);
        return ResponseEntity.noContent().build();
    }
}
