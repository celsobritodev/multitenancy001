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
@RequestMapping("/api/controlplane/me")
@RequiredArgsConstructor
public class ControlPlaneMeController {

    private final ControlPlaneUserService controlPlaneUserService;

    @GetMapping
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_ME_READ.name())")
    public ResponseEntity<ControlPlaneMeResponse> me() {
        return ResponseEntity.ok(controlPlaneUserService.getMe());
    }

    @PatchMapping("/password")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_ME_PASSWORD_CHANGE.name())")
    public ResponseEntity<Void> changeMyPassword(@Valid @RequestBody ControlPlaneChangeMyPasswordRequest request) {
        controlPlaneUserService.changeMyPassword(request);
        return ResponseEntity.noContent().build();
    }
}
