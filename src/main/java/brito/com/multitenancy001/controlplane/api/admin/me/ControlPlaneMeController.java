package brito.com.multitenancy001.controlplane.api.admin.me;

import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneChangeMyPasswordRequest;
import brito.com.multitenancy001.controlplane.api.dto.users.ControlPlaneMeResponse;
import brito.com.multitenancy001.controlplane.application.user.ControlPlaneUserService;
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

    // ✅ usado pelo front mesmo quando mustChangePassword=true
    @GetMapping
    @PreAuthorize("hasAuthority('CP_USER_READ')")
    public ResponseEntity<ControlPlaneMeResponse> me() {
        return ResponseEntity.ok(controlPlaneUserService.getMe());
    }

    // ✅ autenticado: qualquer user CP pode trocar a própria senha
    @PatchMapping("/password")
    @PreAuthorize("hasAuthority('CP_USER_READ')")
    public ResponseEntity<Void> changeMyPassword(@Valid @RequestBody ControlPlaneChangeMyPasswordRequest request) {
        controlPlaneUserService.changeMyPassword(request);
        return ResponseEntity.noContent().build();
    }
}
