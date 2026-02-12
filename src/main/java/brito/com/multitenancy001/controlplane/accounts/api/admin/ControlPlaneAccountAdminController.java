package brito.com.multitenancy001.controlplane.accounts.api.admin;

import brito.com.multitenancy001.controlplane.accounts.api.dto.AccountAdminDetailsResponse;
import brito.com.multitenancy001.controlplane.accounts.api.dto.AccountResponse;
import brito.com.multitenancy001.controlplane.accounts.api.dto.AccountStatusChangeRequest;
import brito.com.multitenancy001.controlplane.accounts.api.dto.summary.AccountTenantUserSummaryResponse;
import brito.com.multitenancy001.controlplane.accounts.api.mapper.AccountAdminDetailsApiMapper;
import brito.com.multitenancy001.controlplane.accounts.api.mapper.AccountApiMapper;
import brito.com.multitenancy001.controlplane.accounts.api.mapper.AccountUserApiMapper;
import brito.com.multitenancy001.controlplane.accounts.app.AccountAppService;
import brito.com.multitenancy001.controlplane.accounts.app.command.AccountStatusChangeCommand;
import brito.com.multitenancy001.controlplane.accounts.app.dto.AccountAdminDetailsProjection;
import brito.com.multitenancy001.controlplane.accounts.app.dto.AccountStatusChangeResponse;
import brito.com.multitenancy001.controlplane.accounts.app.dto.AccountStatusChangeResult;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/controlplane/accounts")
@RequiredArgsConstructor
public class ControlPlaneAccountAdminController {

    private final AccountAppService accountAppService;

    private final AccountApiMapper accountApiMapper;
    private final AccountAdminDetailsApiMapper accountAdminDetailsApiMapper;

    private final AccountUserApiMapper accountUserApiMapper;

    /**
     * Signup via admin (criação de tenant pelo Control Plane).
     */
    @PostMapping("/signup")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_TENANT_CREATE.asAuthority())")
    public ResponseEntity<SignupResponse> createAccount(@Valid @RequestBody SignupRequest req) {

        SignupResult result = accountAppService.createAccount(new SignupCommand(
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
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_TENANT_READ.asAuthority())")
    public ResponseEntity<List<AccountResponse>> listAccounts() {
        List<AccountResponse> out = accountAppService.listAccounts()
                .stream().map(accountApiMapper::toResponse).toList();
        return ResponseEntity.ok(out);
    }

    @GetMapping("/{accountId}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_TENANT_READ.asAuthority())")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable Long accountId) {
        Account a = accountAppService.getAccount(accountId);
        return ResponseEntity.ok(accountApiMapper.toResponse(a));
    }

    @GetMapping("/{accountId}/admin-details")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_TENANT_READ.asAuthority())")
    public ResponseEntity<AccountAdminDetailsResponse> getAdminDetails(@PathVariable Long accountId) {
        AccountAdminDetailsProjection p = accountAppService.getAccountAdminDetails(accountId);
        return ResponseEntity.ok(accountAdminDetailsApiMapper.toResponse(p.account(), p.admin(), p.totalUsers()));
    }

    @PostMapping("/{accountId}/status")
    @PreAuthorize("hasAnyAuthority("
            + "T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_TENANT_SUSPEND.asAuthority(), "
            + "T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_TENANT_RESUME.asAuthority()"
            + ")")
    public ResponseEntity<AccountStatusChangeResponse> changeStatus(
            @PathVariable Long accountId,
            @Valid @RequestBody AccountStatusChangeRequest req
    ) {
        AccountStatusChangeResult r = accountAppService.changeAccountStatus(
                accountId,
                new AccountStatusChangeCommand(req.status())
        );

        // ✅ Response “flat” + tipado 
        return ResponseEntity.ok(AccountStatusChangeResponse.from(r));
    }

    @DeleteMapping("/{accountId}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_TENANT_DELETE.asAuthority())")
    public ResponseEntity<Void> softDelete(@PathVariable Long accountId) {
        accountAppService.softDeleteAccount(accountId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{accountId}/restore")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_TENANT_RESUME.asAuthority())")
    public ResponseEntity<Void> restore(@PathVariable Long accountId) {
        accountAppService.restoreAccount(accountId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{accountId}/tenant-users")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_TENANT_READ.asAuthority())")
    public ResponseEntity<List<AccountTenantUserSummaryResponse>> listTenantUsers(
            @PathVariable Long accountId,
            @RequestParam(name = "onlyOperational", defaultValue = "false") boolean onlyOperational
    ) {
        List<AccountTenantUserSummaryResponse> out = accountAppService
                .listTenantUsers(accountId, onlyOperational)
                .stream()
                .map(accountUserApiMapper::toAccountUserSummary)
                .toList();

        return ResponseEntity.ok(out);
    }

    @PostMapping("/{accountId}/tenant-users/{userId}/suspended-by-admin")
    @PreAuthorize("hasAnyAuthority("
            + "T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_TENANT_SUSPEND.asAuthority(), "
            + "T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_TENANT_RESUME.asAuthority()"
            + ")")
    public ResponseEntity<Void> setSuspendedByAdmin(
            @PathVariable Long accountId,
            @PathVariable Long userId,
            @RequestParam("value") boolean value
    ) {
        accountAppService.setUserSuspendedByAdmin(accountId, userId, value);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/by-status")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_TENANT_READ.asAuthority())")
    public ResponseEntity<Page<AccountResponse>> listByStatus(
            @RequestParam AccountStatus status,
            Pageable pageable
    ) {
        return ResponseEntity.ok(
                accountAppService.listAccountsByStatus(status, pageable).map(accountApiMapper::toResponse)
        );
    }

  @GetMapping("/created-between")
@PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_TENANT_READ.asAuthority())")
public ResponseEntity<Page<AccountResponse>> listCreatedBetween(
        @RequestParam("start") Instant start,
        @RequestParam("end") Instant end,
        Pageable pageable
) {
    return ResponseEntity.ok(
            accountAppService
                    .listAccountsCreatedBetween(start, end, pageable)
                    .map(accountApiMapper::toResponse)
    );
}


    @GetMapping("/search")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_TENANT_READ.asAuthority())")
    public ResponseEntity<Page<AccountResponse>> search(
            @RequestParam("term") String term,
            Pageable pageable
    ) {
        return ResponseEntity.ok(
                accountAppService.searchAccountsByDisplayName(term, pageable).map(accountApiMapper::toResponse)
        );
    }
}
