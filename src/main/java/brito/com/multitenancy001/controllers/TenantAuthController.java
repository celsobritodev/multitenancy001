package brito.com.multitenancy001.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import brito.com.multitenancy001.dtos.ForgotPasswordRequest;
import brito.com.multitenancy001.dtos.JwtResponse;
import brito.com.multitenancy001.dtos.ResetPasswordRequest;
import brito.com.multitenancy001.dtos.TenantLoginRequest;
import brito.com.multitenancy001.services.TenantAuthService;
import brito.com.multitenancy001.services.TenantUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/tenant/auth")
@CrossOrigin(origins = "*", maxAge = 3600)
@RequiredArgsConstructor
public class TenantAuthController {

	private final TenantAuthService tenantAuthService;
	private final TenantUserService tenantUserService;

	@PostMapping("/login")
	public ResponseEntity<JwtResponse> loginTenant(@Valid @RequestBody TenantLoginRequest request) {

		JwtResponse response = tenantAuthService.loginTenant(request);
		return ResponseEntity.ok(response);
	}

	@PostMapping("/forgot-password")
	public ResponseEntity<String> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
	    tenantUserService.generatePasswordResetToken(req.slug(), req.email());
	    return ResponseEntity.ok("Token gerado");
	}

	@PostMapping("/reset-password")
	public ResponseEntity<String> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
	    tenantUserService.resetPasswordWithToken(req.token(), req.newPassword());
	    return ResponseEntity.ok("Senha redefinida com sucesso");
	}
}
