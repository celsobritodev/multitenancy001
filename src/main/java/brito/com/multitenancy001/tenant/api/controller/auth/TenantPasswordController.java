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

    @PostMapping("/forgot")
    public ResponseEntity<String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        tenantUserService.generatePasswordResetToken(req.slug(), req.email());
        return ResponseEntity.ok("Token gerado");
    }

    @PostMapping("/reset")
    public ResponseEntity<String> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        tenantUserService.resetPasswordWithToken(req.token(), req.newPassword());
        return ResponseEntity.ok("Senha redefinida com sucesso");
    }
}
