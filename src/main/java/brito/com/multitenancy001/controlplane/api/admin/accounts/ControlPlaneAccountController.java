package brito.com.multitenancy001.controlplane.api.admin.accounts;

import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountAdminDetailsResponse;
import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountResponse;
import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountStatusChangeRequest;
import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountStatusChangeResponse;
import brito.com.multitenancy001.controlplane.api.dto.users.summary.AccountTenantUserSummaryResponse;
import brito.com.multitenancy001.controlplane.application.AccountLifecycleService;
import brito.com.multitenancy001.controlplane.domain.account.AccountStatus;
import brito.com.multitenancy001.shared.api.error.ApiException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/admin/accounts")
@RequiredArgsConstructor
@Slf4j
public class ControlPlaneAccountController {

    private final AccountLifecycleService accountLifecycleService;

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private Pageable pageableOrDefault(Pageable pageable) {
        if (pageable == null) {
            return PageRequest.of(0, DEFAULT_PAGE_SIZE);
        }
        int page = Math.max(0, pageable.getPageNumber());
        int size = pageable.getPageSize();

        if (size <= 0) size = DEFAULT_PAGE_SIZE;
        if (size > MAX_PAGE_SIZE) size = MAX_PAGE_SIZE;

        return PageRequest.of(page, size, pageable.getSort());
    }

    @GetMapping("/count/active")
    @PreAuthorize("hasAuthority('CP_TENANT_READ')")
    public ResponseEntity<Long> countActiveAccounts() {
        return ResponseEntity.ok(accountLifecycleService.countActiveAccounts());
    }

    @GetMapping("/latest")
    @PreAuthorize("hasAuthority('CP_TENANT_READ')")
    public ResponseEntity<Page<AccountResponse>> listAccountsLatest(Pageable pageable) {
        Pageable p = pageableOrDefault(pageable);
        return ResponseEntity.ok(accountLifecycleService.listAccountsLatest(p));
    }

    @GetMapping("/by-slug/{slug}")
    @PreAuthorize("hasAuthority('CP_TENANT_READ')")
    public ResponseEntity<AccountResponse> getBySlugIgnoreCase(@PathVariable String slug) {
        return ResponseEntity.ok(accountLifecycleService.getAccountBySlugIgnoreCase(slug));
    }

    @GetMapping("/by-status/{status}")
    @PreAuthorize("hasAuthority('CP_TENANT_READ')")
    public ResponseEntity<Page<AccountResponse>> listByStatus(@PathVariable AccountStatus status, Pageable pageable) {
        Pageable p = pageableOrDefault(pageable);
        return ResponseEntity.ok(accountLifecycleService.listAccountsByStatus(status, p));
    }

    @GetMapping("/created-between")
    @PreAuthorize("hasAuthority('CP_TENANT_READ')")
    public ResponseEntity<Page<AccountResponse>> listCreatedBetween(
            @RequestParam("start") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam("end") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            Pageable pageable
    ) {
        Pageable p = pageableOrDefault(pageable);
        return ResponseEntity.ok(accountLifecycleService.listAccountsCreatedBetween(start, end, p));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('CP_TENANT_READ')")
    public ResponseEntity<Page<AccountResponse>> searchByName(
            @RequestParam("term") String term,
            @RequestParam("page") int page,
            @RequestParam("size") int size
    ) {
        if (page < 0) page = 0;

        if (size <= 0) {
            throw new ApiException("INVALID_PAGINATION", "size deve ser > 0", 400);
        }
        if (size > MAX_PAGE_SIZE) {
            throw new ApiException("INVALID_PAGINATION", "size máximo é " + MAX_PAGE_SIZE, 400);
        }

        Pageable p = PageRequest.of(page, size);
        return ResponseEntity.ok(accountLifecycleService.searchAccountsByName(term, p));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CP_TENANT_READ')")
    public ResponseEntity<List<AccountResponse>> listAccounts() {
        log.info("Listando todas as contas");
        return ResponseEntity.ok(accountLifecycleService.listAccounts());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CP_TENANT_READ')")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable Long id) {
        return ResponseEntity.ok(accountLifecycleService.getAccount(id));
    }

    @GetMapping("/{id}/details")
    @PreAuthorize("hasAuthority('CP_TENANT_READ')")
    public ResponseEntity<AccountAdminDetailsResponse> getAccountDetails(@PathVariable Long id) {
        return ResponseEntity.ok(accountLifecycleService.getAccountAdminDetails(id));
    }

    @GetMapping("/{id}/users")
    @PreAuthorize("hasAuthority('CP_TENANT_READ')")
    public ResponseEntity<List<AccountTenantUserSummaryResponse>> listUsersByAccount(@PathVariable Long id) {
        log.info("Listando Usuários por conta");
        return ResponseEntity.ok(accountLifecycleService.listTenantUsers(id, false));
    }

    @GetMapping("/{id}/users/active")
    @PreAuthorize("hasAuthority('CP_TENANT_READ')")
    public ResponseEntity<List<AccountTenantUserSummaryResponse>> listActiveUsersByAccount(@PathVariable Long id) {
        return ResponseEntity.ok(accountLifecycleService.listTenantUsers(id, true));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyAuthority('CP_TENANT_SUSPEND','CP_TENANT_ACTIVATE')")
    public ResponseEntity<AccountStatusChangeResponse> changeAccountStatus(
            @PathVariable Long id,
            @Valid @RequestBody AccountStatusChangeRequest accountStatusChangeRequest
    ) {
        return ResponseEntity.ok(accountLifecycleService.changeAccountStatus(id, accountStatusChangeRequest));
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
