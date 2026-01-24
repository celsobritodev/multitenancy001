package brito.com.multitenancy001.tenant.api.me;

import brito.com.multitenancy001.tenant.api.dto.me.TenantMeResponse;
import brito.com.multitenancy001.tenant.api.dto.me.UpdateMyProfileRequest;
import brito.com.multitenancy001.tenant.application.user.TenantUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tenant/me")
public class TenantMeController {

    private final TenantUserService tenantUserService;

    // ✅ Perfil do usuário logado
    @GetMapping
    public ResponseEntity<TenantMeResponse> me() {
        return ResponseEntity.ok(tenantUserService.getMyProfile());
    }

    // ✅ Atualiza perfil do usuário logado (SAFE whitelist)
    @PutMapping
    public ResponseEntity<TenantMeResponse> update(@Valid @RequestBody UpdateMyProfileRequest req) {
        return ResponseEntity.ok(tenantUserService.updateMyProfile(req));
    }
}
