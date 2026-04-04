package brito.com.multitenancy001.tenant.users.api;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import brito.com.multitenancy001.tenant.users.app.context.TenantUserCurrentContextCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller REST responsável pelos endpoints de ownership e transferência
 * de administração no contexto tenant.
 *
 * <p><b>Responsabilidades:</b></p>
 * <ul>
 *   <li>Expor a operação de transferência de tenant owner.</li>
 *   <li>Delegar a execução ao command service do contexto corrente.</li>
 * </ul>
 *
 * <p><b>Diretrizes arquiteturais:</b></p>
 * <ul>
 *   <li>Não implementa regra de negócio.</li>
 *   <li>Não acessa repositórios diretamente.</li>
 *   <li>Concentra apenas endpoints relacionados à troca de ownership.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/tenant/users")
@RequiredArgsConstructor
@Validated
@Slf4j
public class TenantUserOwnershipController {

    private final TenantUserCurrentContextCommandService tenantUserCommandService;

    /**
     * Transfere a role de tenant owner para outro usuário.
     *
     * @param userId id do usuário destinatário da role
     * @return resposta sem corpo
     */
    @PatchMapping("/{userId:\\d+}/transfer-admin")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_ROLE_TRANSFER.asAuthority())")
    public ResponseEntity<Void> transferTenantOwner(@PathVariable Long userId) {
        log.info("Recebida requisição para transferir role de tenant owner. targetUserId={}", userId);

        tenantUserCommandService.transferTenantOwner(userId);

        log.info("Transferência de role de tenant owner concluída com sucesso. targetUserId={}", userId);

        return ResponseEntity.noContent().build();
    }
}