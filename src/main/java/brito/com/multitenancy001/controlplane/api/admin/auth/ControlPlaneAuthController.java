package brito.com.multitenancy001.controlplane.api.admin.auth;

import brito.com.multitenancy001.controlplane.api.dto.auth.ControlPlaneAdminLoginRequest;
import brito.com.multitenancy001.controlplane.application.ControlPlaneAuthService;
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

    @PostMapping("/login")
    public ResponseEntity<JwtResponse> loginControlPlaneUser(
            @Valid @RequestBody ControlPlaneAdminLoginRequest request
    ) {
        JwtResponse response = controlPlaneAuthService.loginControlPlaneUser(request);
        return ResponseEntity.ok(response);
    }
}
