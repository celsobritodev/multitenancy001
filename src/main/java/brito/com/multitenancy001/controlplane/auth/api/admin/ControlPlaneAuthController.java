package brito.com.multitenancy001.controlplane.auth.api.admin;

import brito.com.multitenancy001.controlplane.auth.api.dto.ControlPlaneAdminLoginRequest;
import brito.com.multitenancy001.controlplane.auth.app.ControlPlaneAuthService;
import brito.com.multitenancy001.shared.api.dto.auth.JwtResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class ControlPlaneAuthController {

    private final ControlPlaneAuthService controlPlaneAuthService;

    // Autentica um usuário do Control Plane e retorna um JWT para chamadas administrativas.
    @PostMapping("/login")
    public ResponseEntity<JwtResponse> loginControlPlaneUser(@Valid @RequestBody ControlPlaneAdminLoginRequest request) {
        JwtResponse response = controlPlaneAuthService.loginControlPlaneUser(request);
        return ResponseEntity.ok(response);
    }
}
