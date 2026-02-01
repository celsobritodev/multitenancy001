package brito.com.multitenancy001.tenant.auth.api;

import brito.com.multitenancy001.shared.api.dto.auth.JwtResponse;
import brito.com.multitenancy001.tenant.auth.api.dto.TenantLoginConfirmRequest;
import brito.com.multitenancy001.tenant.auth.api.dto.TenantLoginInitRequest;
import brito.com.multitenancy001.tenant.auth.api.dto.TenantSelectionOption;
import brito.com.multitenancy001.tenant.auth.api.dto.TenantSelectionRequiredResponse;
import brito.com.multitenancy001.tenant.auth.api.dto.TenantRefreshRequest;
import brito.com.multitenancy001.tenant.auth.app.TenantAuthService;
import brito.com.multitenancy001.tenant.auth.app.dto.TenantLoginResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tenant/auth")
@CrossOrigin(origins = "*", maxAge = 3600)
@RequiredArgsConstructor
public class TenantAuthController {

    private final TenantAuthService tenantAuthService;

    /**
     * 1) email + password
     * - se 1 tenant: retorna JWT (200)
     * - se >1 tenant: retorna TENANT_SELECTION_REQUIRED com details (409)
     */
    @PostMapping("/login")
    public ResponseEntity<?> loginTenant(@Valid @RequestBody TenantLoginInitRequest req) {

        TenantLoginResult result = tenantAuthService.loginInit(req);

        if (result instanceof TenantLoginResult.LoginSuccess ok) {
            return ResponseEntity.ok(ok.jwt());
        }

        if (result instanceof TenantLoginResult.TenantSelectionRequired sel) {
            List<TenantSelectionOption> details = sel.details().stream()
                    .map(o -> new TenantSelectionOption(o.accountId(), o.displayName(), o.slug()))
                    .toList();

            TenantSelectionRequiredResponse body = new TenantSelectionRequiredResponse(
                    "TENANT_SELECTION_REQUIRED",
                    "Selecione a empresa",
                    sel.challengeId(),
                    details
            );

            return ResponseEntity.status(409).body(body);
        }

        // não deve acontecer, mas mantém resiliência
        return ResponseEntity.internalServerError().body(
                new TenantSelectionRequiredResponse(
                        "INTERNAL_ERROR",
                        "Resposta inesperada do servidor",
                        null,
                        null
                )
        );
    }

    /**
     * 2) challengeId + (accountId OU slug)
     * - retorna JWT (200)
     */
    @PostMapping("/login/confirm")
    public ResponseEntity<JwtResponse> confirmTenantLogin(@Valid @RequestBody TenantLoginConfirmRequest req) {
        JwtResponse jwtResponse = tenantAuthService.loginConfirm(req);
        return ResponseEntity.ok(jwtResponse);
    }

    /**
     * 3) refresh token
     * - retorna novo accessToken (200)
     */
    @PostMapping("/refresh")
    public ResponseEntity<JwtResponse> refresh(@Valid @RequestBody TenantRefreshRequest req) {
        JwtResponse jwtResponse = tenantAuthService.refresh(req.refreshToken());
        return ResponseEntity.ok(jwtResponse);
    }
}
