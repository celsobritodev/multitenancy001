package brito.com.multitenancy001.controlplane.api.controller.accounts.admin;

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
@RequiredArgsConstructor
@Slf4j
public class AccountAdminController {

    private final AccountLifecycleService accountLifecycleService;

    @GetMapping
    @PreAuthorize("hasAuthority('CP_TENANT_READ')")
    public ResponseEntity<List<AccountResponse>> listAllAccounts() {
        log.info("Listando todas as contas");
        return ResponseEntity.ok(accountLifecycleService.listAllAccounts());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CP_TENANT_READ')")
    public ResponseEntity<AccountResponse> getAccountById(@PathVariable Long id) {
        return ResponseEntity.ok(accountLifecycleService.getAccountById(id));
    }

    @GetMapping("/{id}/details")
    @PreAuthorize("hasAuthority('CP_TENANT_READ')")
    public ResponseEntity<AccountAdminDetailsResponse> getAccountByIdDetails(@PathVariable Long id) {
        return ResponseEntity.ok(accountLifecycleService.getAccountAdminDetails(id));
    }

    @GetMapping("/{id}/users")
    @PreAuthorize("hasAuthority('CP_TENANT_READ')")
    public ResponseEntity<List<AccountUserSummaryResponse>> listUsersByAccount(@PathVariable Long id) {
        log.info("Listando Usuários por conta");
        return ResponseEntity.ok(accountLifecycleService.listTenantUsers(id, false));
    }

    @GetMapping("/{id}/users/active")
    @PreAuthorize("hasAuthority('CP_TENANT_READ')")
    public ResponseEntity<List<AccountUserSummaryResponse>> listActiveUsersByAccount(@PathVariable Long id) {
        return ResponseEntity.ok(accountLifecycleService.listTenantUsers(id, true));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('CP_TENANT_SUSPEND','CP_TENANT_ACTIVATE')")
    public ResponseEntity<AccountStatusChangeResponse> changeStatusAccount(
            @PathVariable Long id,
            @Valid @RequestBody AccountStatusChangeRequest req
    ) {
        return ResponseEntity.ok(accountLifecycleService.changeAccountStatus(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CP_TENANT_DELETE')")
    public ResponseEntity<Void> softDeleteAccount(@PathVariable Long id) {
        accountLifecycleService.softDeleteAccount(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAuthority('CP_TENANT_ACTIVATE')")
    public ResponseEntity<Void> restoreAccount(@PathVariable Long id) {
        accountLifecycleService.restoreAccount(id);
        return ResponseEntity.noContent().build();
    }
}
