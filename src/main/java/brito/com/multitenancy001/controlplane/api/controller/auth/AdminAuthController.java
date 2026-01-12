package brito.com.multitenancy001.controlplane.api.controller.auth;

import brito.com.multitenancy001.controlplane.api.dto.auth.ControlPlaneAdminLoginRequest;
import brito.com.multitenancy001.controlplane.application.AdminAuthService;
import brito.com.multitenancy001.shared.api.dto.auth.JwtResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

	private final AdminAuthService adminAuthService;

	@PostMapping("/login")
	public ResponseEntity<JwtResponse> loginControlPlaneUser(@Valid @RequestBody ControlPlaneAdminLoginRequest request) {

		JwtResponse response = adminAuthService.loginSuperAdmin(request);
		return ResponseEntity.ok(response);
	}
}
