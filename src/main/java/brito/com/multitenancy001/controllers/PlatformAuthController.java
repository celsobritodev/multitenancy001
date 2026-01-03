package brito.com.multitenancy001.controllers;

import brito.com.multitenancy001.dtos.JwtResponse;
import brito.com.multitenancy001.dtos.SuperAdminLoginRequest;
import brito.com.multitenancy001.services.AdminAuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class PlatformAuthController {

	private final AdminAuthService adminAuthService;

	@PostMapping("/login")
	public ResponseEntity<JwtResponse> loginSuperAdmin(@Valid @RequestBody SuperAdminLoginRequest request) {

		JwtResponse response = adminAuthService.loginSuperAdmin(request);
		return ResponseEntity.ok(response);
	}
}
