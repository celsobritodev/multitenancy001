package brito.com.multitenancy001.tenant.users.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import brito.com.multitenancy001.tenant.users.api.dto.TenantUserSummaryResponse;
import brito.com.multitenancy001.tenant.users.api.mapper.TenantUserApiMapper;
import brito.com.multitenancy001.tenant.users.app.context.TenantUserCurrentContextCommandService;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller REST responsável pelos endpoints de ciclo de vida de usuários tenant.
 *
 * <p><b>Responsabilidades:</b></p>
 * <ul>
 *   <li>Expor operações de soft delete, hard delete e restore.</li>
 *   <li>Preservar endpoint legado de soft delete usado por suítes antigas.</li>
 *   <li>Delegar mutações ao command service do contexto corrente.</li>
 * </ul>
 *
 * <p><b>Diretrizes arquiteturais:</b></p>
 * <ul>
 *   <li>Não implementa regra de negócio.</li>
 *   <li>Não acessa repositórios diretamente.</li>
 *   <li>Foca exclusivamente no lifecycle HTTP do recurso usuário.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/tenant/users")
@RequiredArgsConstructor
@Validated
@Slf4j
public class TenantUserLifecycleController {

    private final TenantUserCurrentContextCommandService tenantUserCommandService;
    private final TenantUserApiMapper tenantUserApiMapper;

    /**
     * Endpoint legado de soft delete para compatibilidade com coleções antigas.
     *
     * <p>Compat:
     * {@code PATCH /api/tenant/users/{id}/soft-delete}</p>
     *
     * @param userId id do usuário
     * @return resposta sem corpo
     */
    @PatchMapping("/{userId:\\d+}/soft-delete")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_USER_DELETE.asAuthority())")
    public ResponseEntity<Void> softDeleteTenantUserCompat(@PathVariable Long userId) {
        log.info("Recebida requisição de soft delete de usuário (rota legada compat). userId={}", userId);

        tenantUserCommandService.softDeleteTenantUser(userId);

        log.info("Soft delete de usuário concluído com sucesso (rota legada compat). userId={}", userId);

        return ResponseEntity.noContent().build();
    }

    /**
     * Endpoint oficial atual de soft delete.
     *
     * @param userId id do usuário
     * @return resposta sem corpo
     */
    @DeleteMapping("/{userId:\\d+}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_USER_DELETE.asAuthority())")
    public ResponseEntity<Void> deleteTenantUser(@PathVariable Long userId) {
        log.info("Recebida requisição de soft delete de usuário. userId={}", userId);

        tenantUserCommandService.softDeleteTenantUser(userId);

        log.info("Soft delete de usuário concluído com sucesso. userId={}", userId);

        return ResponseEntity.noContent().build();
    }

    /**
     * Executa hard delete de usuário.
     *
     * @param userId id do usuário
     * @return resposta sem corpo
     */
    @DeleteMapping("/{userId:\\d+}/hard")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_USER_DELETE.asAuthority())")
    public ResponseEntity<Void> hardDeleteTenantUser(@PathVariable Long userId) {
        log.info("Recebida requisição de hard delete de usuário. userId={}", userId);

        tenantUserCommandService.hardDeleteTenantUser(userId);

        log.info("Hard delete de usuário concluído com sucesso. userId={}", userId);

        return ResponseEntity.noContent().build();
    }

    /**
     * Restaura usuário previamente deletado.
     *
     * @param userId id do usuário
     * @return resumo do usuário restaurado
     */
    @PatchMapping("/{userId:\\d+}/restore")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_USER_RESTORE.asAuthority())")
    public ResponseEntity<TenantUserSummaryResponse> restoreTenantUser(@PathVariable Long userId) {
        log.info("Recebida requisição para restaurar usuário. userId={}", userId);

        TenantUser restored = tenantUserCommandService.restoreTenantUser(userId);

        log.info("Restauração de usuário concluída com sucesso. userId={}", restored.getId());

        return ResponseEntity.ok(tenantUserApiMapper.toSummary(restored));
    }
}