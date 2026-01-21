package brito.com.multitenancy001.tenant.api.controller.auth;

import brito.com.multitenancy001.tenant.api.dto.auth.ForgotPasswordRequest;
import brito.com.multitenancy001.tenant.api.dto.auth.ResetPasswordRequest;
import brito.com.multitenancy001.tenant.application.user.TenantUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tenant/password")
@CrossOrigin(origins = "*", maxAge = 3600)
@RequiredArgsConstructor
public class TenantPasswordController {

    private final TenantUserService tenantUserService;

    // Gera token de reset de senha para usuário tenant (por slug + email).
    @PostMapping("/forgot")
    public ResponseEntity<String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest forgotPasswordRequest) {
        tenantUserService.generatePasswordResetToken(forgotPasswordRequest.slug(), forgotPasswordRequest.email());
        return ResponseEntity.ok("Token gerado");
    }

    // Redefine a senha do usuário tenant validando o token de reset.
    @PostMapping("/reset")
    public ResponseEntity<String> resetPassword(@Valid @RequestBody ResetPasswordRequest resetPasswordRequest) {
        tenantUserService.resetPasswordWithToken(resetPasswordRequest.token(), resetPasswordRequest.newPassword());
        return ResponseEntity.ok("Senha redefinida com sucesso");
    }
}
