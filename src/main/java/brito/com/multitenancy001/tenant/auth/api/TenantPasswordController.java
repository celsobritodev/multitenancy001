package brito.com.multitenancy001.tenant.auth.api;

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
 * Estes endpoints não exigem autenticação JWT.
 * A segurança do fluxo é baseada em token temporário de reset de senha.
 */
@RestController
@RequestMapping("/api/tenant/password")
@CrossOrigin(origins = "*", maxAge = 3600)
@RequiredArgsConstructor
public class TenantPasswordController {

    private final TenantPasswordResetService tenantPasswordResetService;

    @PostMapping("/forgot")
    public ResponseEntity<String> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest forgotPasswordRequest
    ) {
        tenantPasswordResetService.generatePasswordResetToken(
                forgotPasswordRequest.slug(),
                forgotPasswordRequest.email()
        );
        return ResponseEntity.ok("Token gerado");
    }

    @PostMapping("/reset")
    public ResponseEntity<String> resetPassword(
            @Valid @RequestBody ResetPasswordRequest resetPasswordRequest
    ) {
        tenantPasswordResetService.resetPasswordWithToken(
                resetPasswordRequest.token(),
                resetPasswordRequest.newPassword()
        );
        return ResponseEntity.ok("Senha redefinida com sucesso");
    }
}
