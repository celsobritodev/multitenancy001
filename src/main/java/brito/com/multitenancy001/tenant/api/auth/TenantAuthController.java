package brito.com.multitenancy001.tenant.api.auth;

import brito.com.multitenancy001.shared.api.dto.auth.JwtResponse;
import brito.com.multitenancy001.tenant.api.dto.auth.TenantLoginRequest;
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

    // Autentica usuário tenant e retorna JWT (access/refresh) para o contexto do tenant.
    @PostMapping("/login")
    public ResponseEntity<JwtResponse> loginTenant(@Valid @RequestBody TenantLoginRequest tenantLoginRequest) {
        JwtResponse jwtResponse = tenantAuthService.loginTenant(tenantLoginRequest);
        return ResponseEntity.ok(jwtResponse);
    }
}
