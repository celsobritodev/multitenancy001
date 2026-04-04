package brito.com.multitenancy001.tenant.users.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import brito.com.multitenancy001.shared.validation.ValidationPatterns;
import brito.com.multitenancy001.tenant.users.api.dto.TenantUserCreateRequest;
import brito.com.multitenancy001.tenant.users.api.dto.TenantUserDetailsResponse;
import brito.com.multitenancy001.tenant.users.api.dto.TenantUserSummaryResponse;
import brito.com.multitenancy001.tenant.users.api.mapper.TenantUserApiMapper;
import brito.com.multitenancy001.tenant.users.api.validation.TenantUserStatusRequestValidator;
import brito.com.multitenancy001.tenant.users.app.context.TenantUserCurrentContextCommandService;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller REST responsável pelos endpoints de escrita operacional do módulo
 * de usuários tenant.
 *
 * <p><b>Responsabilidades:</b></p>
 * <ul>
 *   <li>Receber requisições HTTP de criação e atualização de usuários tenant.</li>
 *   <li>Validar payloads e parâmetros no boundary HTTP.</li>
 *   <li>Delegar comandos ao command service do contexto corrente.</li>
 *   <li>Mapear entidades de domínio para DTOs de resposta.</li>
 * </ul>
 *
 * <p><b>Diretrizes arquiteturais:</b></p>
 * <ul>
 *   <li>Não implementa regra de negócio.</li>
 *   <li>Não acessa repositórios diretamente.</li>
 *   <li>Não executa lógica de decisão complexa.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/tenant/users")
@RequiredArgsConstructor
@Validated
@Slf4j
public class TenantUserCommandController {

    private final TenantUserCurrentContextCommandService tenantUserCommandService;
    private final TenantUserApiMapper tenantUserApiMapper;
    private final TenantUserStatusRequestValidator tenantUserStatusRequestValidator;

    /**
     * Cria um novo usuário no tenant atual.
     *
     * @param tenantUserCreateRequest payload de criação
     * @return usuário criado
     */
    @PostMapping
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_USER_CREATE.asAuthority())")
    public ResponseEntity<TenantUserDetailsResponse> createTenantUser(
            @Valid @RequestBody TenantUserCreateRequest tenantUserCreateRequest
    ) {
        log.info(
                "Recebida requisição para criação de usuário tenant. email={}, role={}",
                tenantUserCreateRequest.email(),
                tenantUserCreateRequest.role()
        );

        TenantUser created = tenantUserCommandService.createTenantUser(tenantUserCreateRequest);
        TenantUserDetailsResponse response = tenantUserApiMapper.toDetails(created);

        log.info(
                "Usuário tenant criado com sucesso. userId={}, email={}",
                created.getId(),
                created.getEmail()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Atualiza status de suspensão do usuário.
     *
     * <p>Exatamente um dos parâmetros deve ser informado:
     * {@code suspendedByAccount} ou {@code suspendedByAdmin}.</p>
     *
     * @param userId id do usuário
     * @param suspendedByAccount flag de suspensão por conta
     * @param suspendedByAdmin flag de suspensão por admin
     * @return resumo do usuário atualizado
     */
    @PatchMapping("/{userId:\\d+}/status")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_USER_UPDATE.asAuthority())")
    public ResponseEntity<TenantUserSummaryResponse> updateTenantUserStatus(
            @PathVariable Long userId,
            @RequestParam(required = false) Boolean suspendedByAccount,
            @RequestParam(required = false) Boolean suspendedByAdmin
    ) {
        log.info(
                "Recebida requisição para atualizar status de usuário. userId={}, suspendedByAccount={}, suspendedByAdmin={}",
                userId,
                suspendedByAccount,
                suspendedByAdmin
        );

        tenantUserStatusRequestValidator.validateExactlyOneFlag(suspendedByAccount, suspendedByAdmin);

        TenantUser updated = (suspendedByAdmin != null)
                ? tenantUserCommandService.setTenantUserSuspendedByAdmin(userId, suspendedByAdmin)
                : tenantUserCommandService.setTenantUserSuspendedByAccount(userId, suspendedByAccount);

        log.info(
                "Status de usuário atualizado com sucesso. userId={}, suspendedByAccount={}, suspendedByAdmin={}",
                updated.getId(),
                updated.isSuspendedByAccount(),
                updated.isSuspendedByAdmin()
        );

        return ResponseEntity.ok(tenantUserApiMapper.toSummary(updated));
    }

    /**
     * Reseta a senha de um usuário do tenant.
     *
     * @param userId id do usuário
     * @param newPassword nova senha
     * @return resumo do usuário atualizado
     */
    @PatchMapping("/{userId:\\d+}/password")
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
        log.info("Recebida requisição para reset de senha de usuário. userId={}", userId);

        TenantUser updated = tenantUserCommandService.resetTenantUserPassword(userId, newPassword);

        log.info("Reset de senha concluído com sucesso. userId={}", updated.getId());

        return ResponseEntity.ok(tenantUserApiMapper.toSummary(updated));
    }
}