package brito.com.multitenancy001.tenant.auth.api;

import brito.com.multitenancy001.shared.api.dto.auth.JwtResponse;
import brito.com.multitenancy001.shared.auth.app.dto.JwtResult;
import brito.com.multitenancy001.tenant.auth.api.dto.AccountSelectionOption;
import brito.com.multitenancy001.tenant.auth.api.dto.AccountSelectionRequiredResponse;
import brito.com.multitenancy001.tenant.auth.api.dto.TenantLoginConfirmRequest;
import brito.com.multitenancy001.tenant.auth.api.dto.TenantLoginInitRequest;
import brito.com.multitenancy001.tenant.auth.api.dto.TenantRefreshRequest;
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
     * 1) email + password
     * - se 1 conta: retorna JWT (200)
     * - se >1 conta: retorna ACCOUNT_SELECTION_REQUIRED com candidates (409)
     */
    @PostMapping("/login")
    public ResponseEntity<?> loginTenant(@Valid @RequestBody TenantLoginInitRequest req) {

        TenantLoginInitCommand cmd = new TenantLoginInitCommand(req.email(), req.password());
        TenantLoginResult result = tenantAuthService.loginInit(cmd);

        if (result instanceof TenantLoginResult.LoginSuccess ok) {
            return ResponseEntity.ok(toHttp(ok.jwt()));
        }

        if (result instanceof TenantLoginResult.AccountSelectionRequired sel) {
            List<AccountSelectionOption> candidates = sel.candidates().stream()
                    .map(o -> new AccountSelectionOption(o.accountId(), o.displayName(), o.slug()))
                    .toList();

            AccountSelectionRequiredResponse body = new AccountSelectionRequiredResponse(
                    "ACCOUNT_SELECTION_REQUIRED",
                    "Selecione a conta/empresa",
                    sel.challengeId(),
                    candidates
            );

            return ResponseEntity.status(409).body(body);
        }

        return ResponseEntity.internalServerError().body(
                new AccountSelectionRequiredResponse(
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
        TenantLoginConfirmCommand cmd = new TenantLoginConfirmCommand(
                req.challengeId(),
                req.accountId(),
                req.slug()
        );

        JwtResult jwt = tenantAuthService.loginConfirm(cmd);
        return ResponseEntity.ok(toHttp(jwt));
    }

    /**
     * 3) refresh token
     * - retorna novo accessToken (200)
     */
    @PostMapping("/refresh")
    public ResponseEntity<JwtResponse> refresh(@Valid @RequestBody TenantRefreshRequest req) {
        JwtResult jwt = tenantAuthService.refresh(req.refreshToken());
        return ResponseEntity.ok(toHttp(jwt));
    }
}

