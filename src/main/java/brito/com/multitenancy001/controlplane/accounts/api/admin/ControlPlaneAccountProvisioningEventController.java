package brito.com.multitenancy001.controlplane.accounts.api.admin;

import brito.com.multitenancy001.controlplane.accounts.api.dto.AccountProvisioningEventResponse;
import brito.com.multitenancy001.controlplane.accounts.app.query.AccountProvisioningEventQueryService;
import brito.com.multitenancy001.controlplane.accounts.app.query.dto.AccountProvisioningEventData;
import brito.com.multitenancy001.controlplane.accounts.domain.ProvisioningStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/controlplane/accounts/{accountId}/provisioning-events")
@RequiredArgsConstructor
public class ControlPlaneAccountProvisioningEventController {

    private final AccountProvisioningEventQueryService accountProvisioningEventQueryService;

    private static AccountProvisioningEventResponse toHttp(AccountProvisioningEventData d) {
        return new AccountProvisioningEventResponse(
                d.id(),
                d.accountId(),
                d.status(),
                d.failureCode(),
                d.message(),
                d.detailsJson(),
                d.createdAt()
        );
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_TENANT_READ.name())")
    public ResponseEntity<Page<AccountProvisioningEventResponse>> list(@PathVariable Long accountId, Pageable pageable) {
        return ResponseEntity.ok(
                accountProvisioningEventQueryService.listByAccount(accountId, pageable).map(ControlPlaneAccountProvisioningEventController::toHttp)
        );
    }

    @GetMapping("/latest")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.controlplane.security.ControlPlanePermission).CP_TENANT_READ.name())")
    public ResponseEntity<AccountProvisioningEventResponse> latest(
            @PathVariable Long accountId,
            @RequestParam(name = "status", required = false) ProvisioningStatus status
    ) {
        Optional<AccountProvisioningEventData> d = accountProvisioningEventQueryService.getLatestByAccount(accountId, status);
        return d.map(x -> ResponseEntity.ok(toHttp(x)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
