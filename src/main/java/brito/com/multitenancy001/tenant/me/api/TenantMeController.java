package brito.com.multitenancy001.tenant.me.api;

import brito.com.multitenancy001.tenant.me.api.dto.TenantChangeMyPasswordRequest;
import brito.com.multitenancy001.tenant.me.api.dto.TenantMeResponse;
import brito.com.multitenancy001.tenant.me.api.dto.UpdateMyProfileRequest;
import brito.com.multitenancy001.tenant.me.api.mapper.TenantMeApiMapper;
import brito.com.multitenancy001.tenant.me.app.TenantMeService;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tenant/me")
public class TenantMeController {

    private final TenantMeService tenantMeService;
    private final TenantMeApiMapper tenantMeApiMapper;

    // ✅ Perfil do usuário logado
    @GetMapping
    public ResponseEntity<TenantMeResponse> me() {
        TenantUser tenantUser = tenantMeService.getMyProfile();
        return ResponseEntity.ok(tenantMeApiMapper.toMe(tenantUser));
    }

    // ✅ Atualiza perfil do usuário logado (SAFE whitelist)
    @PutMapping
    public ResponseEntity<TenantMeResponse> update(@Valid @RequestBody UpdateMyProfileRequest req) {
        TenantUser tenantUser = tenantMeService.updateMyProfile(req);
        return ResponseEntity.ok(tenantMeApiMapper.toMe(tenantUser));
    }

    // ✅ Troca minha senha (JWT) - destrava mustChangePassword
    @PatchMapping("/password")
    public ResponseEntity<Void> changeMyPassword(@Valid @RequestBody TenantChangeMyPasswordRequest req) {
        tenantMeService.changeMyPassword(req);
        return ResponseEntity.noContent().build();
    }
}
