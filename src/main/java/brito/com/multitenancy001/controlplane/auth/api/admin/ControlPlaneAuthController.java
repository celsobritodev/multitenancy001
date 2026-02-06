package brito.com.multitenancy001.controlplane.auth.api.admin;

import brito.com.multitenancy001.controlplane.auth.api.dto.ControlPlaneAdminLoginRequest;
import brito.com.multitenancy001.controlplane.auth.app.ControlPlaneAuthService;
import brito.com.multitenancy001.controlplane.auth.app.command.ControlPlaneAdminLoginCommand;
import brito.com.multitenancy001.shared.api.dto.auth.JwtResponse;
import brito.com.multitenancy001.shared.auth.app.dto.JwtResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/controlplane/auth")
@CrossOrigin(origins = "*", maxAge = 3600)
@RequiredArgsConstructor
public class ControlPlaneAuthController {

    private final ControlPlaneAuthService controlPlaneAuthService;

    private static JwtResponse toHttp(JwtResult r) {
        return JwtResponse.forEmailLogin(
                r.accessToken(),
                r.refreshToken(),
                r.userId(),
                r.email(),
                r.role(),
                r.accountId(),
                r.tenantSchema()
        );
    }

    @PostMapping("/login")
    public ResponseEntity<JwtResponse> login(@Valid @RequestBody ControlPlaneAdminLoginRequest req) {
        JwtResult jwt = controlPlaneAuthService.loginControlPlaneUser(
                new ControlPlaneAdminLoginCommand(req.email(), req.password())
        );
        return ResponseEntity.ok(toHttp(jwt));
    }
}
