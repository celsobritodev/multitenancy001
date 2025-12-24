package brito.com.multitenancy001.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import brito.com.multitenancy001.dtos.JwtResponse;
import brito.com.multitenancy001.dtos.TenantLoginRequest;
import brito.com.multitenancy001.services.TenantAuthService;
import brito.com.multitenancy001.services.UserTenantService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/tenant/auth")
@CrossOrigin(origins = "*", maxAge = 3600)
@RequiredArgsConstructor
public class TenantAuthController {

	private final TenantAuthService tenantAuthService;
	private final UserTenantService tenantUserService;

	@PostMapping("/login")
	public ResponseEntity<JwtResponse> loginTenant(@Valid @RequestBody TenantLoginRequest request) {

		JwtResponse response = tenantAuthService.loginTenant(request);
		return ResponseEntity.ok(response);
	}

	@PostMapping("/forgot-password")
	public ResponseEntity<String> forgotPassword(@RequestParam String email) {
		tenantUserService.generatePasswordResetToken(email);
		return ResponseEntity.ok("Token gerado");
	}

	@PostMapping("/reset-password")
	public ResponseEntity<String> resetPassword(@RequestParam String token, @RequestParam String newPassword) {

		tenantUserService.resetPasswordWithToken(token, newPassword);
		return ResponseEntity.ok("Senha redefinida com sucesso");
	}
}
