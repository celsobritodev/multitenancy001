package brito.com.multitenancy001.tenant.users.api;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.validation.ValidationPatterns;
import brito.com.multitenancy001.tenant.users.api.dto.TenantUserCreateRequest;
import brito.com.multitenancy001.tenant.users.api.dto.TenantUserDetailsResponse;
import brito.com.multitenancy001.tenant.users.api.dto.TenantUserSummaryResponse;
import brito.com.multitenancy001.tenant.users.api.mapper.TenantUserApiMapper;
import brito.com.multitenancy001.tenant.users.app.command.TenantUserCommandService;
import brito.com.multitenancy001.tenant.users.app.query.TenantUserQueryService;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tenant/users")
public class TenantUserController {

    private final TenantUserQueryService tenantUserQueryService;
    private final TenantUserCommandService tenantUserCommandService;
    private final TenantUserApiMapper tenantUserApiMapper;

    @GetMapping("/{userId}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_USER_READ.asAuthority())")
    public ResponseEntity<TenantUserDetailsResponse> getTenantUser(@PathVariable Long userId) {
        TenantUser user = tenantUserQueryService.getUser(userId);
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
            throw new ApiException(ApiErrorCode.INVALID_STATUS, "Informe suspendedByAccount ou suspendedByAdmin");
        }
        if (suspendedByAccount != null && suspendedByAdmin != null) {
            throw new ApiException(ApiErrorCode.INVALID_STATUS, "Informe apenas um: suspendedByAccount ou suspendedByAdmin");
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
}
