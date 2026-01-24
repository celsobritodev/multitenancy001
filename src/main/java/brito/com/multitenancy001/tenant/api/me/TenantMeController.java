package brito.com.multitenancy001.tenant.api.me;

import brito.com.multitenancy001.tenant.api.dto.me.UpdateMyProfileRequest;
import brito.com.multitenancy001.tenant.api.dto.users.TenantUserDetailsResponse;
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
    public ResponseEntity<TenantUserDetailsResponse> me() {
        return ResponseEntity.ok(tenantUserService.getMyProfile());
    }

    // ✅ Atualiza perfil do usuário logado (principalmente "name")
    @PutMapping
    public ResponseEntity<TenantUserDetailsResponse> update(@Valid @RequestBody UpdateMyProfileRequest req) {
        TenantUserDetailsResponse updated = tenantUserService.updateMyProfile(
                req.name(),
                req.phone(),
                req.locale(),
                req.timezone()
        );
        return ResponseEntity.ok(updated);
    }
}
