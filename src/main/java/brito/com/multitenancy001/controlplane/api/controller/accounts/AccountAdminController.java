package brito.com.multitenancy001.controlplane.api.controller.accounts;

import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountAdminDetailsResponse;
import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountResponse;
import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountStatusChangeRequest;
import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountStatusChangeResponse;
import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountUserSummaryResponse;
import brito.com.multitenancy001.controlplane.application.AccountLifecycleService;
import jakarta.validation.Valid;
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
public class AccountAdminController {

	private final AccountLifecycleService accountLifecycleService;
	

	@GetMapping("/{id}/users")
	public ResponseEntity<List<AccountUserSummaryResponse>> listUsersByAccount(@PathVariable Long id) {
		log.info("Listando Usuários por conta");
		return ResponseEntity.ok(accountLifecycleService.listTenantUsers(id, false));
	}

	@GetMapping("/{id}/users/active")
	public ResponseEntity<List<AccountUserSummaryResponse>> listActiveUsersByAccount(@PathVariable Long id) {
		return ResponseEntity.ok(accountLifecycleService.listTenantUsers(id, true));
	}

	@PatchMapping("/{id}/status")
	public ResponseEntity<AccountStatusChangeResponse> changeStatusAccount(
	        @PathVariable Long id,
	        @Valid @RequestBody AccountStatusChangeRequest req) {
	    return ResponseEntity.ok(accountLifecycleService.changeAccountStatus(id, req));
	}

	@GetMapping
	public ResponseEntity<List<AccountResponse>> listAllAccounts() {
		log.info("Listando todas as contas");
		return ResponseEntity.ok(accountLifecycleService.listAllAccounts());
	}

	@GetMapping("/{id}")
	public ResponseEntity<AccountResponse> getAccountById(@PathVariable Long id) {
		return ResponseEntity.ok(accountLifecycleService.getAccountByIdWithAdmin(id));
	}

	@GetMapping("/{id}/details")
	public ResponseEntity<AccountAdminDetailsResponse> getAccountByIdDetails(@PathVariable Long id) {
		return ResponseEntity.ok(accountLifecycleService.getAccountAdminDetails(id));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> softDeleteAccount(@PathVariable Long id) {
		accountLifecycleService.softDeleteAccount(id);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{id}/restore")
	public ResponseEntity<Void> restoreAccount(@PathVariable Long id) {
		accountLifecycleService.restoreAccount(id);
		return ResponseEntity.noContent().build();
	}
}