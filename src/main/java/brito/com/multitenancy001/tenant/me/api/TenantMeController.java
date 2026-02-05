package brito.com.multitenancy001.tenant.me.api;


import brito.com.multitenancy001.tenant.me.api.dto.TenantChangeMyPasswordRequest;
import brito.com.multitenancy001.tenant.me.api.dto.TenantMeResponse;
import brito.com.multitenancy001.tenant.me.api.dto.UpdateMyProfileRequest;
import brito.com.multitenancy001.tenant.users.app.TenantUserFacade;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tenant/me")
public class TenantMeController {

    private final TenantUserFacade tenantUserFacade;

    // ✅ Perfil do usuário logado
    @GetMapping
    public ResponseEntity<TenantMeResponse> me() {
        return ResponseEntity.ok(tenantUserFacade.getMyProfile());
    }

    // ✅ Atualiza perfil do usuário logado (SAFE whitelist)
    @PutMapping
    public ResponseEntity<TenantMeResponse> update(@Valid @RequestBody UpdateMyProfileRequest req) {
        return ResponseEntity.ok(tenantUserFacade.updateMyProfile(req));
    }

    // ✅ Troca minha senha (JWT) - destrava mustChangePassword
    @PatchMapping("/password")
    public ResponseEntity<Void> changeMyPassword(@Valid @RequestBody TenantChangeMyPasswordRequest req) {
        tenantUserFacade.changeMyPassword(req);
        return ResponseEntity.noContent().build();
    }
}
