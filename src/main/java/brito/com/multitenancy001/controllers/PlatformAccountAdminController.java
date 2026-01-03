package brito.com.multitenancy001.controllers;

import brito.com.multitenancy001.dtos.*;
import brito.com.multitenancy001.services.AccountProvisioningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/accounts")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class PlatformAccountAdminController {

	private final AccountProvisioningService accountProvisioningService;

	@GetMapping("/{id}/users")
	public ResponseEntity<List<TenantUserResponse>> listUsersByAccount(@PathVariable Long id) {
		return ResponseEntity.ok(accountProvisioningService.listTenantUsers(id, false));
	}

	@GetMapping("/{id}/users/active")
	public ResponseEntity<List<TenantUserResponse>> listActiveUsersByAccount(@PathVariable Long id) {
		return ResponseEntity.ok(accountProvisioningService.listTenantUsers(id, true));
	}

	@PatchMapping("/{id}/status")
	public ResponseEntity<AccountStatusChangeResponse> changeStatusAccount(@PathVariable Long id,
			@RequestBody StatusRequest req) {
		return ResponseEntity.ok(accountProvisioningService.changeAccountStatus(id, req));
	}

	@GetMapping
	public ResponseEntity<List<AccountResponse>> listAllAccounts() {
		return ResponseEntity.ok(accountProvisioningService.listAllAccounts());
	}

	@GetMapping("/{id}")
	public ResponseEntity<AccountResponse> getAccountById(@PathVariable Long id) {
		return ResponseEntity.ok(accountProvisioningService.getAccountByIdWithAdmin(id));
	}

	@GetMapping("/{id}/details")
	public ResponseEntity<AccountAdminDetailsResponse> getAccountByIdDetails(@PathVariable Long id) {
		return ResponseEntity.ok(accountProvisioningService.getAccountAdminDetails(id));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> softDeleteAccount(@PathVariable Long id) {
		accountProvisioningService.softDeleteAccount(id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{id}/restore")
	public ResponseEntity<Void> restoreAccount(@PathVariable Long id) {
		accountProvisioningService.restoreAccount(id);
		return ResponseEntity.noContent().build();
	}
}