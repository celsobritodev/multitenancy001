package brito.com.multitenancy001.tenant.users.api;

import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.validation.ValidationPatterns;
import brito.com.multitenancy001.tenant.users.api.dto.TenantUserCreateRequest;
import brito.com.multitenancy001.tenant.users.api.dto.TenantUserDetailsResponse;
import brito.com.multitenancy001.tenant.users.api.dto.TenantUserListItemResponse;
import brito.com.multitenancy001.tenant.users.api.dto.TenantUserSummaryResponse;
import brito.com.multitenancy001.tenant.users.api.dto.TenantUsersListResponse;
import brito.com.multitenancy001.tenant.users.api.mapper.TenantUserApiMapper;
import brito.com.multitenancy001.tenant.users.app.command.TenantUserCommandService;
import brito.com.multitenancy001.tenant.users.app.query.TenantUserQueryService;
import brito.com.multitenancy001.tenant.users.app.query.TenantUsersListView;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tenant/users")
@RequiredArgsConstructor
public class TenantUserController {

    private final TenantUserQueryService tenantUserQueryService;
    private final TenantUserCommandService tenantUserCommandService;
    private final TenantUserApiMapper tenantUserApiMapper;

    @GetMapping
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_USER_READ.asAuthority())")
    public ResponseEntity<TenantUsersListResponse> listTenantUsers() {
        TenantUsersListView view = tenantUserQueryService.listTenantUsers();

        List<TenantUserListItemResponse> mapped = view.users().stream()
                .map(u -> view.isOwner()
                        ? tenantUserApiMapper.toListItemRich(u)
                        : tenantUserApiMapper.toListItemBasic(u))
                .toList();

        return ResponseEntity.ok(new TenantUsersListResponse(view.entitlements(), mapped));
    }

    @GetMapping("/enabled")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_USER_READ.asAuthority())")
    public ResponseEntity<List<TenantUserSummaryResponse>> listEnabledTenantUsers() {
        List<TenantUser> users = tenantUserQueryService.listEnabledTenantUsers();
        return ResponseEntity.ok(users.stream().map(tenantUserApiMapper::toSummary).toList());
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_USER_READ.asAuthority())")
    public ResponseEntity<TenantUserDetailsResponse> getTenantUser(@PathVariable Long userId) {
        TenantUser user = tenantUserQueryService.getTenantUser(userId);
        return ResponseEntity.ok(tenantUserApiMapper.toDetails(user));
    }

    @PostMapping
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_USER_CREATE.asAuthority())")
    public ResponseEntity<TenantUserDetailsResponse> createTenantUser(@Valid @RequestBody TenantUserCreateRequest tenantUserCreateRequest) {
        TenantUser created = tenantUserCommandService.createTenantUser(tenantUserCreateRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(tenantUserApiMapper.toDetails(created));
    }

    @PatchMapping("/{userId}/transfer-admin")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_ROLE_TRANSFER.asAuthority())")
    public ResponseEntity<Void> transferTenantOwner(@PathVariable Long userId) {
        tenantUserCommandService.transferTenantOwner(userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{userId}/status")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_USER_UPDATE.asAuthority())")
    public ResponseEntity<TenantUserSummaryResponse> updateTenantUserStatus(
            @PathVariable Long userId,
            @RequestParam(required = false) Boolean suspendedByAccount,
            @RequestParam(required = false) Boolean suspendedByAdmin
    ) {
        if (suspendedByAccount == null && suspendedByAdmin == null) {
            throw new ApiException("INVALID_STATUS", "Informe suspendedByAccount ou suspendedByAdmin", 400);
        }
        if (suspendedByAccount != null && suspendedByAdmin != null) {
            throw new ApiException("INVALID_STATUS", "Informe apenas um dos parâmetros (suspendedByAccount OU suspendedByAdmin)", 400);
        }

        TenantUser updated =
                (suspendedByAdmin != null)
                        ? tenantUserCommandService.setTenantUserSuspendedByAdmin(userId, suspendedByAdmin)
                        : tenantUserCommandService.setTenantUserSuspendedByAccount(userId, suspendedByAccount);

        return ResponseEntity.ok(tenantUserApiMapper.toSummary(updated));
    }

    @PatchMapping("/{userId}/password")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_USER_UPDATE.asAuthority())")
    public ResponseEntity<TenantUserSummaryResponse> resetTenantUserPassword(
            @PathVariable Long userId,
            @RequestParam
            @Pattern(
                    regexp = ValidationPatterns.PASSWORD_PATTERN,
                    message = "Senha fraca. Use pelo menos 8 caracteres com letras maiúsculas, minúsculas e números"
            )
            String newPassword
    ) {
        TenantUser updated = tenantUserCommandService.resetTenantUserPassword(userId, newPassword);
        return ResponseEntity.ok(tenantUserApiMapper.toSummary(updated));
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_USER_DELETE.asAuthority())")
    public ResponseEntity<Void> deleteTenantUser(@PathVariable Long userId) {
        tenantUserCommandService.softDeleteTenantUser(userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{userId}/hard")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_USER_DELETE.asAuthority())")
    public ResponseEntity<Void> hardDeleteTenantUser(@PathVariable Long userId) {
        tenantUserCommandService.hardDeleteTenantUser(userId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{userId}/restore")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_USER_RESTORE.asAuthority())")
    public ResponseEntity<TenantUserSummaryResponse> restoreTenantUser(@PathVariable Long userId) {
        TenantUser restored = tenantUserCommandService.restoreTenantUser(userId);
        return ResponseEntity.ok(tenantUserApiMapper.toSummary(restored));
    }

    @GetMapping("/enabled/{userId}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_USER_READ.asAuthority())")
    public ResponseEntity<TenantUserDetailsResponse> getEnabledTenantUser(@PathVariable Long userId) {
        TenantUser user = tenantUserQueryService.getEnabledTenantUser(userId);
        return ResponseEntity.ok(tenantUserApiMapper.toDetails(user));
    }

    @GetMapping("/enabled/count")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_USER_READ.asAuthority())")
    public ResponseEntity<Long> countEnabledTenantUsers() {
        return ResponseEntity.ok(tenantUserQueryService.countEnabledTenantUsers());
    }
}
