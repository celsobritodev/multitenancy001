package brito.com.multitenancy001.tenant.me.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import brito.com.multitenancy001.tenant.me.api.dto.TenantChangeMyPasswordRequest;
import brito.com.multitenancy001.tenant.me.api.dto.TenantMeResponse;
import brito.com.multitenancy001.tenant.me.api.dto.UpdateMyProfileRequest;
import brito.com.multitenancy001.tenant.me.api.mapper.TenantMeApiMapper;
import brito.com.multitenancy001.tenant.me.app.TenantMeService;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tenant/me")
public class TenantMeController {

    private final TenantMeService tenantMeService;
    private final TenantMeApiMapper tenantMeApiMapper;

    // ✅ Perfil do usuário logado
    @GetMapping
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_USER_READ.name())")
    public ResponseEntity<TenantMeResponse> me() {
        TenantUser tenantUser = tenantMeService.getMyProfile();
        return ResponseEntity.ok(tenantMeApiMapper.toMe(tenantUser));
    }

    // ✅ Atualiza perfil do usuário logado (SAFE whitelist)
    @PutMapping
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_USER_UPDATE.name())")
    public ResponseEntity<TenantMeResponse> update(@Valid @RequestBody UpdateMyProfileRequest req) {
        TenantUser tenantUser = tenantMeService.updateMyProfile(req);
        return ResponseEntity.ok(tenantMeApiMapper.toMe(tenantUser));
    }

    // ✅ Troca minha senha (JWT) - destrava mustChangePassword
    @PatchMapping("/password")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_USER_UPDATE.name())")
    public ResponseEntity<Void> changeMyPassword(@Valid @RequestBody TenantChangeMyPasswordRequest req) {
        tenantMeService.changeMyPassword(req);
        return ResponseEntity.noContent().build();
    }
}
