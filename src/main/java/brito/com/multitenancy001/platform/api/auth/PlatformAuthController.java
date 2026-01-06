package brito.com.multitenancy001.platform.api.auth;

import brito.com.multitenancy001.platform.api.dto.auth.PlatformAdminLoginRequest;
import brito.com.multitenancy001.platform.application.AdminAuthService;
import brito.com.multitenancy001.shared.api.dto.auth.JwtResponse;
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
	public ResponseEntity<JwtResponse> loginSuperAdmin(@Valid @RequestBody PlatformAdminLoginRequest request) {

		JwtResponse response = adminAuthService.loginSuperAdmin(request);
		return ResponseEntity.ok(response);
	}
}
