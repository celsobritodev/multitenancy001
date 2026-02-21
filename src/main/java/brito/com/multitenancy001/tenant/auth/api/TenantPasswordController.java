package brito.com.multitenancy001.tenant.auth.api;

import brito.com.multitenancy001.shared.api.dto.GenericMessageResponse;
import brito.com.multitenancy001.tenant.auth.api.dto.ForgotPasswordRequest;
import brito.com.multitenancy001.tenant.auth.api.dto.ResetPasswordRequest;
import brito.com.multitenancy001.tenant.auth.app.TenantPasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints responsáveis pelo fluxo de recuperação e redefinição
 * de senha de usuários TENANT.
 *
 * Regras:
 * - Estes endpoints não exigem autenticação JWT.
 * - A segurança do fluxo é baseada em token temporário de reset de senha.
 * - Respostas são padronizadas via DTO (contrato consistente).
 */
@RestController
@RequestMapping("/api/tenant/password")
@CrossOrigin(origins = "*", maxAge = 3600)
@RequiredArgsConstructor
public class TenantPasswordController {

    private final TenantPasswordResetService tenantPasswordResetService;

    @PostMapping("/forgot")
    public ResponseEntity<GenericMessageResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest forgotPasswordRequest
    ) {
        /* Gera token de reset e dispara o mecanismo associado (e-mail etc) via service. */
        tenantPasswordResetService.generatePasswordResetToken(
                forgotPasswordRequest.slug(),
                forgotPasswordRequest.email()
        );

        return ResponseEntity.ok(new GenericMessageResponse("Token gerado"));
    }

    @PostMapping("/reset")
    public ResponseEntity<GenericMessageResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequest resetPasswordRequest
    ) {
        /* Valida token e redefine a senha via service (nenhuma regra sensível fica no controller). */
        tenantPasswordResetService.resetPasswordWithToken(
                resetPasswordResetTokenSafe(resetPasswordRequest.token()),
                resetPasswordRequest.newPassword()
        );

        return ResponseEntity.ok(new GenericMessageResponse("Senha redefinida com sucesso"));
    }

    /**
     * Normalização defensiva do token para evitar edge-cases triviais de input.
     */
    private String resetPasswordResetTokenSafe(String token) {
        /* Normaliza input para evitar falhas por whitespace e manter consistência de auditoria. */
        return (token == null ? null : token.trim());
    }
}