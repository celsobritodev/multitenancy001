package brito.com.multitenancy001.controlplane.accounts.api.admin;

import brito.com.multitenancy001.controlplane.accounts.api.dto.AccountAdminDetailsResponse;
import brito.com.multitenancy001.controlplane.accounts.api.dto.AccountResponse;
import brito.com.multitenancy001.controlplane.accounts.api.dto.AccountStatusChangeRequest;
import brito.com.multitenancy001.controlplane.accounts.api.dto.AccountStatusChangeResponse;
import brito.com.multitenancy001.controlplane.accounts.api.dto.summary.AccountTenantUserSummaryResponse;
import brito.com.multitenancy001.controlplane.accounts.api.mapper.AccountAdminDetailsApiMapper;
import brito.com.multitenancy001.controlplane.accounts.api.mapper.AccountApiMapper;
import brito.com.multitenancy001.controlplane.accounts.app.AccountLifecycleService;
import brito.com.multitenancy001.controlplane.accounts.app.command.AccountStatusChangeCommand;
import brito.com.multitenancy001.controlplane.accounts.app.dto.AccountAdminDetailsProjection;
import brito.com.multitenancy001.controlplane.accounts.app.dto.AccountStatusChangeResult;
import brito.com.multitenancy001.controlplane.accounts.app.dto.AccountTenantUserSummaryData;
import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.AccountStatus;
import brito.com.multitenancy001.controlplane.signup.api.dto.SignupRequest;
import brito.com.multitenancy001.controlplane.signup.api.dto.SignupResponse;
import brito.com.multitenancy001.controlplane.signup.api.dto.TenantAdminResponse;
import brito.com.multitenancy001.controlplane.signup.app.command.SignupCommand;
import brito.com.multitenancy001.controlplane.signup.app.dto.SignupResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/controlplane/accounts")
@RequiredArgsConstructor
public class ControlPlaneAccountController {

    private final AccountLifecycleService accountLifecycleService;

    private final AccountApiMapper accountApiMapper;
    private final AccountAdminDetailsApiMapper accountAdminDetailsApiMapper;

    // signup via admin (se existir)
    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> createAccount(@Valid @RequestBody SignupRequest req) {

        SignupResult result = accountLifecycleService.createAccount(new SignupCommand(
                req.displayName(),
                req.loginEmail(),
                req.taxIdType(),
                req.taxIdNumber(),
                req.password(),
                req.confirmPassword()
        ));

        SignupResponse http = new SignupResponse(
                accountApiMapper.toResponse(result.account()),
                new TenantAdminResponse(
                        result.tenantAdmin().id(),
                        result.tenantAdmin().email(),
                        result.tenantAdmin().role()
                )
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(http);
    }

    @GetMapping
    public ResponseEntity<List<AccountResponse>> listAccounts() {
        List<AccountResponse> out = accountLifecycleService.listAccounts()
                .stream().map(accountApiMapper::toResponse).toList();
        return ResponseEntity.ok(out);
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable Long accountId) {
        Account a = accountLifecycleService.getAccount(accountId);
        return ResponseEntity.ok(accountApiMapper.toResponse(a));
    }

    @GetMapping("/{accountId}/admin-details")
    public ResponseEntity<AccountAdminDetailsResponse> getAdminDetails(@PathVariable Long accountId) {
        AccountAdminDetailsProjection p = accountLifecycleService.getAccountAdminDetails(accountId);
        return ResponseEntity.ok(accountAdminDetailsApiMapper.toResponse(p.account(), p.admin(), p.totalUsers()));
    }

    @PostMapping("/{accountId}/status")
    public ResponseEntity<AccountStatusChangeResponse> changeStatus(
            @PathVariable Long accountId,
            @Valid @RequestBody AccountStatusChangeRequest req
    ) {
        AccountStatusChangeResult r = accountLifecycleService.changeAccountStatus(
                accountId,
                new AccountStatusChangeCommand(req.status())
        );

        AccountStatusChangeResponse http = new AccountStatusChangeResponse(
                r.accountId(),
                r.newStatus(),
                r.previousStatus(),
                r.changedAt(),
                r.tenantSchema(),
                new AccountStatusChangeResponse.SideEffects(
                        r.tenantUsersUpdated(),
                        r.action(),
                        r.affectedUsers()
                )
        );

        return ResponseEntity.ok(http);
    }

    @DeleteMapping("/{accountId}")
    public ResponseEntity<Void> softDelete(@PathVariable Long accountId) {
        accountLifecycleService.softDeleteAccount(accountId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{accountId}/restore")
    public ResponseEntity<Void> restore(@PathVariable Long accountId) {
        accountLifecycleService.restoreAccount(accountId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{accountId}/tenant-users")
    public ResponseEntity<List<AccountTenantUserSummaryResponse>> listTenantUsers(
            @PathVariable Long accountId,
            @RequestParam(name = "onlyOperational", defaultValue = "false") boolean onlyOperational
    ) {
        List<AccountTenantUserSummaryResponse> out = accountLifecycleService.listTenantUsers(accountId, onlyOperational)
                .stream().map(ControlPlaneAccountController::toHttpTenantUser).toList();
        return ResponseEntity.ok(out);
    }

    @PostMapping("/{accountId}/tenant-users/{userId}/suspended-by-admin")
    public ResponseEntity<Void> setSuspendedByAdmin(
            @PathVariable Long accountId,
            @PathVariable Long userId,
            @RequestParam("value") boolean value
    ) {
        accountLifecycleService.setUserSuspendedByAdmin(accountId, userId, value);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/by-status")
    public ResponseEntity<Page<AccountResponse>> listByStatus(
            @RequestParam AccountStatus status,
            Pageable pageable
    ) {
        return ResponseEntity.ok(accountLifecycleService.listAccountsByStatus(status, pageable).map(accountApiMapper::toResponse));
    }

    @GetMapping("/created-between")
    public ResponseEntity<Page<AccountResponse>> listCreatedBetween(
            @RequestParam("start") String startIso,
            @RequestParam("end") String endIso,
            Pageable pageable
    ) {
        LocalDateTime start = LocalDateTime.parse(startIso);
        LocalDateTime end = LocalDateTime.parse(endIso);
        return ResponseEntity.ok(accountLifecycleService.listAccountsCreatedBetween(start, end, pageable).map(accountApiMapper::toResponse));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<AccountResponse>> search(
            @RequestParam("term") String term,
            Pageable pageable
    ) {
        return ResponseEntity.ok(accountLifecycleService.searchAccountsByDisplayName(term, pageable).map(accountApiMapper::toResponse));
    }

    private static AccountTenantUserSummaryResponse toHttpTenantUser(AccountTenantUserSummaryData d) {
        return new AccountTenantUserSummaryResponse(
                d.id(), d.accountId(), d.name(), d.email(), d.role(),
                d.suspendedByAccount(), d.suspendedByAdmin(), d.enabled()
        );
    }
}
