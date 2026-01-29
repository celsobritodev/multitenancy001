package brito.com.multitenancy001.tenant.api.auth;

import brito.com.multitenancy001.shared.api.dto.auth.JwtResponse;
import brito.com.multitenancy001.tenant.api.dto.auth.TenantLoginAmbiguousResponse;
import brito.com.multitenancy001.tenant.api.dto.auth.TenantLoginConfirmRequest;
import brito.com.multitenancy001.tenant.api.dto.auth.TenantLoginInitRequest;
import brito.com.multitenancy001.tenant.application.auth.TenantAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tenant/auth")
@CrossOrigin(origins = "*", maxAge = 3600)
@RequiredArgsConstructor
public class TenantAuthController {

    private final TenantAuthService tenantAuthService;

    /**
     * 1) email + password
     * - se 1 tenant: retorna JWT (200)
     * - se >1 tenant: retorna challengeId + candidates (409)
     */
    @PostMapping("/login")
    public ResponseEntity<?> loginTenant(@Valid @RequestBody TenantLoginInitRequest req) {

        Object result = tenantAuthService.loginInit(req);

        if (result instanceof JwtResponse jwt) {
            return ResponseEntity.ok(jwt);
        }

        if (result instanceof TenantLoginAmbiguousResponse ambiguous) {
            return ResponseEntity.status(409).body(ambiguous);
        }

        return ResponseEntity.internalServerError().body(
                new TenantLoginAmbiguousResponse(
                        "INTERNAL_ERROR",
                        "Resposta inesperada do servidor",
                        null,
                        null
                )
        );
    }

    /**
     * 2) challengeId + accountId
     * - retorna JWT (200)
     */
    @PostMapping("/login/confirm")
    public ResponseEntity<JwtResponse> confirmTenantLogin(@Valid @RequestBody TenantLoginConfirmRequest req) {
        JwtResponse jwtResponse = tenantAuthService.loginConfirm(req);
        return ResponseEntity.ok(jwtResponse);
    }
}
