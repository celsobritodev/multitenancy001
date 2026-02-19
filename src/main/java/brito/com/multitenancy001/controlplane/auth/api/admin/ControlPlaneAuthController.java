package brito.com.multitenancy001.controlplane.auth.api.admin;

import brito.com.multitenancy001.controlplane.auth.api.dto.ControlPlaneAdminLoginRequest;
import brito.com.multitenancy001.controlplane.auth.api.dto.ControlPlaneRefreshRequest;
import brito.com.multitenancy001.controlplane.auth.app.ControlPlaneAuthService;
import brito.com.multitenancy001.controlplane.auth.app.ControlPlaneLogoutService;
import brito.com.multitenancy001.controlplane.auth.app.ControlPlaneTokenRefreshService;
import brito.com.multitenancy001.controlplane.auth.app.command.ControlPlaneAdminLoginCommand;
import brito.com.multitenancy001.shared.api.dto.auth.JwtResponse;
import brito.com.multitenancy001.shared.api.dto.auth.LogoutRequest;
import brito.com.multitenancy001.shared.auth.app.dto.JwtResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * API de autenticação do Control Plane.
 *
 * Endpoints:
 * - POST /login: login com email+senha
 * - POST /refresh: refresh com rotação (novo refreshToken)
 * - POST /logout: logout forte (revoga sessão server-side)
 */
@RestController
@RequestMapping("/api/controlplane/auth")
@CrossOrigin(origins = "*", maxAge = 3600)
@RequiredArgsConstructor
public class ControlPlaneAuthController {

    private final ControlPlaneAuthService controlPlaneAuthService;
    private final ControlPlaneTokenRefreshService controlPlaneTokenRefreshService;
    private final ControlPlaneLogoutService controlPlaneLogoutService;

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
        /** comentário: autentica usuário de plataforma e retorna access+refresh */
        JwtResult jwt = controlPlaneAuthService.loginControlPlaneUser(
                new ControlPlaneAdminLoginCommand(req.email(), req.password())
        );
        return ResponseEntity.ok(toHttp(jwt));
    }

    @PostMapping("/refresh")
    public ResponseEntity<JwtResponse> refresh(@Valid @RequestBody ControlPlaneRefreshRequest req) {
        /** comentário: refresh com rotação server-side */
        JwtResult jwt = controlPlaneTokenRefreshService.refresh(req.refreshToken()); // ✅ AQUI o fix
        return ResponseEntity.ok(toHttp(jwt));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest req) {
        /** comentário: logout forte (revoga sessão/refresh no servidor) */
        controlPlaneLogoutService.logout(req.refreshToken(), req.allDevices());
        return ResponseEntity.noContent().build();
    }
}
