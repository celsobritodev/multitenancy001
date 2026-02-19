package brito.com.multitenancy001.tenant.auth.api;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.api.dto.auth.JwtResponse;
import brito.com.multitenancy001.shared.api.dto.auth.LogoutRequest;
import brito.com.multitenancy001.shared.auth.app.AuthRefreshSessionService;
import brito.com.multitenancy001.shared.auth.app.dto.JwtResult;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.auth.api.dto.TenantLoginConfirmRequest;
import brito.com.multitenancy001.tenant.auth.api.dto.TenantLoginInitRequest;
import brito.com.multitenancy001.tenant.auth.api.dto.TenantRefreshRequest;
import brito.com.multitenancy001.tenant.auth.api.dto.TenantSelectionOption;
import brito.com.multitenancy001.tenant.auth.api.dto.TenantSelectionRequiredResponse;
import brito.com.multitenancy001.tenant.auth.app.TenantLoginConfirmService;
import brito.com.multitenancy001.tenant.auth.app.TenantLoginInitService;
import brito.com.multitenancy001.tenant.auth.app.TenantLogoutService;
import brito.com.multitenancy001.tenant.auth.app.TenantTokenRefreshService;
import brito.com.multitenancy001.tenant.auth.app.command.TenantLoginConfirmCommand;
import brito.com.multitenancy001.tenant.auth.app.command.TenantLoginInitCommand;
import brito.com.multitenancy001.tenant.auth.app.dto.TenantLoginResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * API de autenticação do Tenant.
 *
 * Regras:
 * - /login pode retornar JWT final (single-tenant) OU exigir seleção (409)
 * - /login/confirm emite JWT final quando houve seleção
 * - /refresh rotaciona refresh token (novo refresh)
 * - /logout faz logout forte (revoga sessão/refresh no servidor)
 *
 * Ajuste:
 * - registra sessão server-side tanto no /login (quando retornar JWT final)
 *   quanto no /login/confirm.
 */
@RestController
@RequestMapping("/api/tenant/auth")
@CrossOrigin(origins = "*", maxAge = 3600)
@RequiredArgsConstructor
public class TenantAuthController {

    private final TenantLoginInitService tenantLoginInitService;
    private final TenantLoginConfirmService tenantLoginConfirmService;
    private final TenantTokenRefreshService tenantTokenRefreshService;

    private final TenantLogoutService tenantLogoutService;
    private final AuthRefreshSessionService refreshSessions;

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
     * - Se só 1 tenant válido -> 200 + JWT FINAL
     * - Se >1 tenant válido -> 409 + TENANT_SELECTION_REQUIRED + challengeId + details[]
     */
    @PostMapping("/login")
    public ResponseEntity<?> loginTenant(@Valid @RequestBody TenantLoginInitRequest req) {

        /** comentário: inicia login tenant; pode emitir JWT final ou exigir seleção */
        TenantLoginInitCommand cmd = new TenantLoginInitCommand(req.email(), req.password());
        TenantLoginResult result = tenantLoginInitService.loginInit(cmd);

        if (result instanceof TenantLoginResult.LoginSuccess ok) {
            JwtResult jwt = ok.jwt();

            // ✅ /login pode retornar JWT final => registra sessão server-side
            refreshSessions.onRefreshIssued(
                    "TENANT",
                    jwt.accountId(),
                    jwt.userId(),
                    jwt.tenantSchema(),
                    jwt.refreshToken()
            );

            return ResponseEntity.ok(toHttp(jwt));
        }

        if (result instanceof TenantLoginResult.TenantSelectionRequired sel) {
            List<TenantSelectionOption> details = sel.details().stream()
                    .map(o -> new TenantSelectionOption(o.accountId(), o.displayName(), o.slug()))
                    .toList();

            TenantSelectionRequiredResponse body = new TenantSelectionRequiredResponse(
                    ApiErrorCode.TENANT_SELECTION_REQUIRED.name(),
                    "Selecione a empresa/tenant para continuar",
                    sel.challengeId(),
                    details
            );

            return ResponseEntity.status(409).body(body);
        }

        throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "Resposta inesperada do servidor", 500);
    }

    /**
     * POST /api/tenant/auth/login/confirm com challengeId + slug (ou accountId)
     * Autentica somente no tenant escolhido (JWT final).
     */
    @PostMapping("/login/confirm")
    public ResponseEntity<JwtResponse> confirmTenantLogin(@Valid @RequestBody TenantLoginConfirmRequest req) {

        /** comentário: confirma seleção e emite tokens definitivos */
        TenantLoginConfirmCommand cmd = new TenantLoginConfirmCommand(
                req.challengeId().toString(),
                req.accountId(),
                req.slug()
        );

        JwtResult jwt = tenantLoginConfirmService.loginConfirm(cmd);

        // ✅ registra sessão server-side (logout forte / rotação)
        refreshSessions.onRefreshIssued(
                "TENANT",
                jwt.accountId(),
                jwt.userId(),
                jwt.tenantSchema(),
                jwt.refreshToken()
        );

        return ResponseEntity.ok(toHttp(jwt));
    }

    /**
     * refresh token -> novo accessToken + NOVO refreshToken (rotação)
     */
    @PostMapping("/refresh")
    public ResponseEntity<JwtResponse> refresh(@Valid @RequestBody TenantRefreshRequest req) {
        /** comentário: refresh com rotação server-side */
        JwtResult jwt = tenantTokenRefreshService.refresh(req.refreshToken());
        return ResponseEntity.ok(toHttp(jwt));
    }

    /**
     * Logout forte:
     * - revoga refreshToken no servidor
     * - allDevices=true revoga todas as sessões do usuário no domínio TENANT
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest req) {
        /** comentário: executa logout forte */
        tenantLogoutService.logout(req.refreshToken(), req.allDevices());
        return ResponseEntity.noContent().build();
    }
}
