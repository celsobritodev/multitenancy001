package brito.com.multitenancy001.controllers;

import brito.com.multitenancy001.dtos.*;
import brito.com.multitenancy001.services.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/accounts")
@PreAuthorize("hasAuthority('TOKEN_ACCOUNT') and hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AdminAccountsController {

	private final AccountService accountService;

	@GetMapping("/{id}/users")
	public ResponseEntity<List<TenantUserResponse>> listUsersByAccount(@PathVariable Long id) {

		return ResponseEntity.ok(accountService.listTenantUsers(id, false));
	}

	@GetMapping("/{id}/users/active")
	public ResponseEntity<List<TenantUserResponse>> listActiveUsersByAccount(@PathVariable Long id) {

		return ResponseEntity.ok(accountService.listTenantUsers(id, true));
	}

	@PatchMapping("/{id}/status")
	public ResponseEntity<AccountStatusChangeResponse> changeStatusAccount(@PathVariable Long id,
			@RequestBody StatusRequest req) {
		return ResponseEntity.ok(accountService.changeAccountStatus(id, req));
	}

	@GetMapping
	public ResponseEntity<List<AccountResponse>> listAllAccounts() {
		return ResponseEntity.ok(accountService.listAllAccounts());
	}

	//
	@GetMapping("/{id}")
	public ResponseEntity<AccountResponse> getAccountById(@PathVariable Long id) {
		return ResponseEntity.ok(accountService.getAccountByIdWithAdmin(id));
	}

	@GetMapping("/{id}/details")
	public ResponseEntity<AccountAdminDetailsResponse> getAccountByIdDetails(@PathVariable Long id) {
		return ResponseEntity.ok(accountService.getAccountAdminDetails(id));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> softDeleteAccount(@PathVariable Long id) {
		accountService.softDeleteAccount(id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{id}/restore")
	public ResponseEntity<Void> restoreAccount(@PathVariable Long id) {
		accountService.restoreAccount(id);
		return ResponseEntity.noContent().build();

	}
}
