package brito.com.multitenancy001.tenant.auth.api;

import brito.com.multitenancy001.shared.api.dto.auth.JwtResponse;
import brito.com.multitenancy001.shared.auth.app.dto.JwtResult;
import brito.com.multitenancy001.tenant.auth.api.dto.TenantLoginConfirmRequest;
import brito.com.multitenancy001.tenant.auth.api.dto.TenantLoginInitRequest;
import brito.com.multitenancy001.tenant.auth.api.dto.TenantRefreshRequest;
import brito.com.multitenancy001.tenant.auth.api.dto.TenantSelectionOption;
import brito.com.multitenancy001.tenant.auth.api.dto.TenantSelectionRequiredResponse;
import brito.com.multitenancy001.tenant.auth.app.TenantAuthService;
import brito.com.multitenancy001.tenant.auth.app.command.TenantLoginConfirmCommand;
import brito.com.multitenancy001.tenant.auth.app.command.TenantLoginInitCommand;
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

    private static JwtResponse toHttp(JwtResult r) {
        if (r == null) return null;
        return new JwtResponse(
                r.accessToken(),
                r.refreshToken(),
                r.tokenType(),
                r.userId(),
                r.email(),
                r.role(),
                r.accountId(),
                r.tenantSchema()
        );
    }

    /**
     * POST /api/tenant/auth/login com email + password
     *
     * - Se só 1 tenant válido -> 200 + JWT
     * - Se >1 tenant válido -> 409 + TENANT_SELECTION_REQUIRED + challengeId + details[]
     */
    @PostMapping("/login")
    public ResponseEntity<?> loginTenant(@Valid @RequestBody TenantLoginInitRequest req) {

        TenantLoginInitCommand cmd = new TenantLoginInitCommand(req.email(), req.password());
        TenantLoginResult result = tenantAuthService.loginInit(cmd);

        if (result instanceof TenantLoginResult.LoginSuccess ok) {
            return ResponseEntity.ok(toHttp(ok.jwt()));
        }

        if (result instanceof TenantLoginResult.TenantSelectionRequired sel) {
            List<TenantSelectionOption> details = sel.details().stream()
                    .map(o -> new TenantSelectionOption(o.accountId(), o.displayName(), o.slug()))
                    .toList();

            TenantSelectionRequiredResponse body = new TenantSelectionRequiredResponse(
                    "TENANT_SELECTION_REQUIRED",
                    "Selecione a empresa/tenant para continuar",
                    sel.challengeId(),
                    details
            );

            return ResponseEntity.status(409).body(body);
        }

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
     * POST /api/tenant/auth/login/confirm com challengeId + slug (ou accountId)
     * Autentica somente no tenant escolhido
     */
    @PostMapping("/login/confirm")
    public ResponseEntity<JwtResponse> confirmTenantLogin(@Valid @RequestBody TenantLoginConfirmRequest req) {

        // UUID -> String (seu command já recebe String)
        TenantLoginConfirmCommand cmd = new TenantLoginConfirmCommand(
                req.challengeId().toString(),
                req.accountId(),
                req.slug()
        );

        JwtResult jwt = tenantAuthService.loginConfirm(cmd);
        return ResponseEntity.ok(toHttp(jwt));
    }

    /**
     * refresh token -> novo accessToken
     */
    @PostMapping("/refresh")
    public ResponseEntity<JwtResponse> refresh(@Valid @RequestBody TenantRefreshRequest req) {
        JwtResult jwt = tenantAuthService.refresh(req.refreshToken());
        return ResponseEntity.ok(toHttp(jwt));
    }
}
