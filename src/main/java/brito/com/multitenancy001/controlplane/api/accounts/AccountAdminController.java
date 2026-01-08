package brito.com.multitenancy001.controlplane.api.accounts;

import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountAdminDetailsResponse;
import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountResponse;
import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountStatusChangeRequest;
import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountStatusChangeResponse;
import brito.com.multitenancy001.controlplane.application.AccountLifecycleService;
import brito.com.multitenancy001.tenant.api.dto.users.TenantUserSummaryResponse;
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

	private final AccountLifecycleService accountProvisioningService;
	

	@GetMapping("/{id}/users")
	public ResponseEntity<List<TenantUserSummaryResponse>> listUsersByAccount(@PathVariable Long id) {
		return ResponseEntity.ok(accountProvisioningService.listTenantUsers(id, false));
	}

	@GetMapping("/{id}/users/active")
	public ResponseEntity<List<TenantUserSummaryResponse>> listActiveUsersByAccount(@PathVariable Long id) {
		return ResponseEntity.ok(accountProvisioningService.listTenantUsers(id, true));
	}

	@PatchMapping("/{id}/status")
	public ResponseEntity<AccountStatusChangeResponse> changeStatusAccount(@PathVariable Long id,
			@RequestBody AccountStatusChangeRequest req) {
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