package brito.com.multitenancy001.tenant.auth.api;

import brito.com.multitenancy001.tenant.auth.api.dto.ForgotPasswordRequest;
import brito.com.multitenancy001.tenant.auth.api.dto.ResetPasswordRequest;
import brito.com.multitenancy001.tenant.users.app.TenantUserFacade;
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

    private final TenantUserFacade tenantUserFacade;

    /**
     * Inicia o fluxo de recuperação de senha para um usuário TENANT.
     *
     * Recebe o slug do tenant e o email do usuário, valida a existência
     * e gera um token temporário para redefinição de senha.
     *
     * Este endpoint é normalmente utilizado na funcionalidade
     * "Esqueci minha senha".
     */
    @PostMapping("/forgot")
    public ResponseEntity<String> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest forgotPasswordRequest
    ) {
        tenantUserFacade.generatePasswordResetToken(
                forgotPasswordRequest.slug(),
                forgotPasswordRequest.email()
        );
        return ResponseEntity.ok("Token gerado");
    }

    /**
     * Finaliza o fluxo de redefinição de senha utilizando um token válido.
     *
     * Valida o token, atualiza a senha do usuário e encerra o fluxo
     * de recuperação, permitindo que o usuário volte a se autenticar.
     */
    @PostMapping("/reset")
    public ResponseEntity<String> resetPassword(
            @Valid @RequestBody ResetPasswordRequest resetPasswordRequest
    ) {
        tenantUserFacade.resetPasswordWithToken(
                resetPasswordRequest.token(),
                resetPasswordRequest.newPassword()
        );
        return ResponseEntity.ok("Senha redefinida com sucesso");
    }
}
