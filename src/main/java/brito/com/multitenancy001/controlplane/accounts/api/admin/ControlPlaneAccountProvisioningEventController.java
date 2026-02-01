package brito.com.multitenancy001.controlplane.accounts.api.admin;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import brito.com.multitenancy001.controlplane.accounts.api.dto.AccountProvisioningEventResponse;
import brito.com.multitenancy001.controlplane.accounts.app.query.AccountProvisioningEventQueryService;
import brito.com.multitenancy001.controlplane.accounts.domain.ProvisioningStatus;

@RestController
@RequestMapping("/api/admin/accounts")
public class ControlPlaneAccountProvisioningEventController {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final AccountProvisioningEventQueryService queryService;

    public ControlPlaneAccountProvisioningEventController(AccountProvisioningEventQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/{id}/provisioning-events")
    @PreAuthorize("hasAuthority('CP_TENANT_READ')")
    public ResponseEntity<Page<AccountProvisioningEventResponse>> listProvisioningEvents(
            @PathVariable("id") Long accountId,
            Pageable pageable
    ) {
        Pageable p = pageableOrDefaultWithCreatedAtDesc(pageable);
        return ResponseEntity.ok(queryService.listByAccount(accountId, p));
    }

    @GetMapping("/{id}/provisioning-events/latest")
    @PreAuthorize("hasAuthority('CP_TENANT_READ')")
    public ResponseEntity<AccountProvisioningEventResponse> getLatestProvisioningEvent(
            @PathVariable("id") Long accountId,
            @RequestParam(name = "requireStatus", required = false) ProvisioningStatus requireStatus
    ) {
        return queryService
                .getLatestByAccount(accountId, requireStatus)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    private Pageable pageableOrDefaultWithCreatedAtDesc(Pageable pageable) {
        if (pageable == null) {
            return PageRequest.of(0, DEFAULT_PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt"));
        }

        int page = Math.max(0, pageable.getPageNumber());
        int size = pageable.getPageSize();

        if (size <= 0) size = DEFAULT_PAGE_SIZE;
        if (size > MAX_PAGE_SIZE) size = MAX_PAGE_SIZE;

        Sort sort = pageable.getSort();
        if (sort == null || sort.isUnsorted()) {
            sort = Sort.by(Sort.Direction.DESC, "createdAt");
        }

        return PageRequest.of(page, size, sort);
    }
}
