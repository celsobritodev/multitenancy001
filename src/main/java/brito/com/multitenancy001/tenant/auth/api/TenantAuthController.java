// src/main/java/brito/com/multitenancy001/tenant/auth/api/TenantAuthController.java
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
import brito.com.multitenancy001.tenant.auth.app.TenantLoginConfirmFacade;
import brito.com.multitenancy001.tenant.auth.app.TenantLoginInitService;
import brito.com.multitenancy001.tenant.auth.app.TenantLogoutService;
import brito.com.multitenancy001.tenant.auth.app.TenantTokenRefreshService;
import brito.com.multitenancy001.tenant.auth.app.command.TenantLoginConfirmCommand;
import brito.com.multitenancy001.tenant.auth.app.command.TenantLoginInitCommand;
import brito.com.multitenancy001.tenant.auth.app.dto.TenantLoginResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@RestController
@RequestMapping("/api/tenant/auth")
@CrossOrigin(origins = "*", maxAge = 3600)
@RequiredArgsConstructor
public class TenantAuthController {

    private final TenantLoginInitService tenantLoginInitService;
    private final TenantLoginConfirmFacade tenantLoginConfirmService;
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
        log.info("🔐 [LOGIN] Tentativa de login para email: {}", req.email());
        
        TenantLoginInitCommand cmd = new TenantLoginInitCommand(req.email(), req.password());
        TenantLoginResult result = tenantLoginInitService.loginInit(cmd);
        
        log.info("📦 [LOGIN] Resultado do loginInit: {}", result.getClass().getSimpleName());

        if (result instanceof TenantLoginResult.LoginSuccess ok) {
            JwtResult jwt = ok.jwt();
            log.info("✅ [LOGIN] Login direto bem-sucedido - accountId={}, userId={}", 
                     jwt.accountId(), jwt.userId());

            refreshSessions.onRefreshIssued(
                    brito.com.multitenancy001.shared.auth.domain.AuthSessionDomain.TENANT,
                    jwt.accountId(),
                    jwt.userId(),
                    jwt.tenantSchema(),
                    jwt.refreshToken()
            );
            log.info("🔄 [LOGIN] Sessão registrada para userId={}", jwt.userId());

            return ResponseEntity.ok(toHttp(jwt));
        }

        if (result instanceof TenantLoginResult.TenantSelectionRequired sel) {
            log.info("🔍 [LOGIN] Seleção de tenant necessária - challengeId={}", sel.challengeId());
            log.info("📋 [LOGIN] Detalhes recebidos do service: {}", sel.details());
            
            List<TenantSelectionOption> details = sel.details().stream()
                    .map(o -> {
                        log.debug("   - Opção: accountId={}, displayName={}, slug={}", 
                                  o.accountId(), o.displayName(), o.slug());
                        return new TenantSelectionOption(o.accountId(), o.displayName(), o.slug());
                    })
                    .toList();
            
            log.info("📋 [LOGIN] Detalhes mapeados: {} opções", details.size());
            details.forEach(d -> log.info("   → accountId={}, slug={}", d.accountId(), d.slug()));

            TenantSelectionRequiredResponse body = new TenantSelectionRequiredResponse(
                    ApiErrorCode.TENANT_SELECTION_REQUIRED.name(),
                    "Selecione a empresa/tenant para continuar",
                    sel.challengeId(),
                    details
            );
            
            log.info("📦 [LOGIN] Response body: code={}, challengeId={}, details.size={}", 
                     body.code(), body.challengeId(), body.details().size());

            // ✅ ADICIONAR HEADERS DE DEBUG
            return ResponseEntity.status(409)
                    .header("X-Debug-ChallengeId", sel.challengeId())
                    .header("X-Debug-Details-Count", String.valueOf(details.size()))
                    .header("X-Debug-Account-Ids", 
                            details.stream()
                                   .map(d -> String.valueOf(d.accountId()))
                                   .reduce((a, b) -> a + "," + b)
                                   .orElse(""))
                    .body(body);
        }

        log.error("❌ [LOGIN] Resultado inesperado: {}", result.getClass().getName());
        throw new ApiException(ApiErrorCode.INTERNAL_ERROR, "Resposta inesperada do servidor", 500);
    }

    /**
     * POST /api/tenant/auth/login/confirm com challengeId + slug (ou accountId)
     * Autentica somente no tenant escolhido (JWT final).
     */
    @PostMapping("/login/confirm")
    public ResponseEntity<JwtResponse> confirmTenantLogin(@Valid @RequestBody TenantLoginConfirmRequest req) {
        log.info("🔐 [CONFIRM] Confirmando login - challengeId={}, accountId={}, slug={}", 
                 req.challengeId(), req.accountId(), req.slug());

        TenantLoginConfirmCommand cmd = new TenantLoginConfirmCommand(
                req.challengeId().toString(),
                req.accountId(),
                req.slug()
        );

        JwtResult jwt = tenantLoginConfirmService.loginConfirm(cmd);
        log.info("✅ [CONFIRM] Login confirmado - accountId={}, userId={}", 
                 jwt.accountId(), jwt.userId());

        refreshSessions.onRefreshIssued(
                brito.com.multitenancy001.shared.auth.domain.AuthSessionDomain.TENANT,
                jwt.accountId(),
                jwt.userId(),
                jwt.tenantSchema(),
                jwt.refreshToken()
        );
        log.info("🔄 [CONFIRM] Sessão registrada para userId={}", jwt.userId());

        return ResponseEntity.ok(toHttp(jwt));
    }

    /**
     * refresh token -> novo accessToken + NOVO refreshToken (rotação)
     */
    @PostMapping("/refresh")
    public ResponseEntity<JwtResponse> refresh(@Valid @RequestBody TenantRefreshRequest req) {
        log.info("🔄 [REFRESH] Refresh token request");
        JwtResult jwt = tenantTokenRefreshService.refresh(req.refreshToken());
        log.info("✅ [REFRESH] Token renovado - accountId={}, userId={}", 
                 jwt.accountId(), jwt.userId());
        return ResponseEntity.ok(toHttp(jwt));
    }

    /**
     * Logout forte:
     * - revoga refreshToken no servidor
     * - allDevices=true revoga todas as sessões do usuário no domínio TENANT
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest req) {
        log.info("🚪 [LOGOUT] Logout - allDevices={}", req.allDevices());
        tenantLogoutService.logout(req.refreshToken(), req.allDevices());
        log.info("✅ [LOGOUT] Logout concluído");
        return ResponseEntity.noContent().build();
    }
}