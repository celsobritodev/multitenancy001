package brito.com.multitenancy001.tenant.users.api;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.validation.ValidationPatterns;
import brito.com.multitenancy001.tenant.users.api.dto.TenantUserCreateRequest;
import brito.com.multitenancy001.tenant.users.api.dto.TenantUserDetailsResponse;
import brito.com.multitenancy001.tenant.users.api.dto.TenantUserListItemResponse;
import brito.com.multitenancy001.tenant.users.api.dto.TenantUserSummaryResponse;
import brito.com.multitenancy001.tenant.users.api.dto.TenantUsersListResponse;
import brito.com.multitenancy001.tenant.users.api.mapper.TenantUserApiMapper;
import brito.com.multitenancy001.tenant.users.app.context.TenantUserCurrentContextCommandService;
import brito.com.multitenancy001.tenant.users.app.context.TenantUserCurrentContextQueryService;
import brito.com.multitenancy001.tenant.users.app.query.TenantUsersListView;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller REST do módulo de usuários no contexto tenant.
 *
 * <p><b>Responsabilidades:</b></p>
 * <ul>
 *   <li>Receber requisições HTTP do módulo de usuários tenant.</li>
 *   <li>Validar payloads e parâmetros de entrada no boundary HTTP.</li>
 *   <li>Delegar operações de leitura ao query service de contexto atual.</li>
 *   <li>Delegar operações de escrita ao command service de contexto atual.</li>
 *   <li>Mapear entidades de domínio para DTOs de resposta via mapper da API.</li>
 * </ul>
 *
 * <p><b>Diretrizes arquiteturais:</b></p>
 * <ul>
 *   <li>O controller não implementa regra de negócio.</li>
 *   <li>O controller não acessa repositórios.</li>
 *   <li>O controller não manipula diretamente transações tenant/public.</li>
 *   <li>Compatibilidades legadas de rota são preservadas para as suítes E2E/Newman.</li>
 * </ul>
 *
 * <p><b>Observação importante sobre roteamento:</b></p>
 * <ul>
 *   <li>Rotas fixas devem existir explicitamente.</li>
 *   <li>Rotas com path variable usam regex numérica para evitar captura indevida
 *       de strings e falhas por {@code TypeMismatch}.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/tenant/users")
@RequiredArgsConstructor
@Validated
@Slf4j
public class TenantUserController {

    private final TenantUserCurrentContextQueryService tenantUserQueryService;
    private final TenantUserCurrentContextCommandService tenantUserCommandService;
    private final TenantUserApiMapper tenantUserApiMapper;

    /**
     * Lista usuários do tenant corrente.
     *
     * <p>Quando o usuário autenticado é owner, a listagem pode ser enriquecida
     * com permissões e dados adicionais. Caso contrário, retorna versão básica.</p>
     *
     * @return resposta consolidada de listagem
     */
    @GetMapping
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_USER_READ.asAuthority())")
    public ResponseEntity<TenantUsersListResponse> listTenantUsers() {
        log.info("Recebida requisição para listar usuários do tenant atual");

        TenantUsersListView view = tenantUserQueryService.listTenantUsers();

        List<TenantUserListItemResponse> mapped = view.users().stream()
                .map(user -> view.isOwner()
                        ? tenantUserApiMapper.toListItemRich(user)
                        : tenantUserApiMapper.toListItemBasic(user))
                .toList();

        log.info(
                "Listagem de usuários do tenant concluída com sucesso. totalUsers={}, requesterIsOwner={}",
                mapped.size(),
                view.isOwner()
        );

        return ResponseEntity.ok(new TenantUsersListResponse(view.entitlements(), mapped));
    }

    /**
     * Lista apenas usuários habilitados do tenant atual.
     *
     * @return lista resumida de usuários habilitados
     */
    @GetMapping("/enabled")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_USER_READ.asAuthority())")
    public ResponseEntity<List<TenantUserSummaryResponse>> listEnabledTenantUsers() {
        log.info("Recebida requisição para listar usuários habilitados do tenant atual");

        List<TenantUser> users = tenantUserQueryService.listEnabledTenantUsers();
        List<TenantUserSummaryResponse> response = users.stream()
                .map(tenantUserApiMapper::toSummary)
                .toList();

        log.info("Listagem de usuários habilitados concluída com sucesso. totalUsers={}", response.size());

        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint de compatibilidade com coleções E2E legadas.
     *
     * <p>Compat:
     * {@code GET /api/tenant/users/count-enabled}</p>
     *
     * @return quantidade de usuários habilitados
     */
    @GetMapping("/count-enabled")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_USER_READ.asAuthority())")
    public ResponseEntity<Long> countEnabledTenantUsersCompat() {
        log.info("Recebida requisição de contagem de usuários habilitados (rota legada compat)");

        Long count = tenantUserQueryService.countEnabledTenantUsers();

        log.info("Contagem de usuários habilitados concluída com sucesso. count={}", count);

        return ResponseEntity.ok(count);
    }

    /**
     * Endpoint oficial de contagem de usuários habilitados.
     *
     * <p>Oficial:
     * {@code GET /api/tenant/users/enabled/count}</p>
     *
     * @return quantidade de usuários habilitados
     */
    @GetMapping("/enabled/count")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_USER_READ.asAuthority())")
    public ResponseEntity<Long> countEnabledTenantUsers() {
        log.info("Recebida requisição de contagem de usuários habilitados");

        Long count = tenantUserQueryService.countEnabledTenantUsers();

        log.info("Contagem de usuários habilitados concluída com sucesso. count={}", count);

        return ResponseEntity.ok(count);
    }

    /**
     * Busca um usuário habilitado por id.
     *
     * @param userId id do usuário
     * @return detalhes do usuário habilitado
     */
    @GetMapping("/enabled/{userId:\\d+}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_USER_READ.asAuthority())")
    public ResponseEntity<TenantUserDetailsResponse> getEnabledTenantUser(@PathVariable Long userId) {
        log.info("Recebida requisição para buscar usuário habilitado. userId={}", userId);

        TenantUser user = tenantUserQueryService.getEnabledTenantUser(userId);
        TenantUserDetailsResponse response = tenantUserApiMapper.toDetails(user);

        log.info("Usuário habilitado carregado com sucesso. userId={}", userId);

        return ResponseEntity.ok(response);
    }

    /**
     * Busca qualquer usuário do tenant por id.
     *
     * @param userId id do usuário
     * @return detalhes do usuário
     */
    @GetMapping("/{userId:\\d+}")
    @PreAuthorize("hasAuthority(T(brito.com.multitenancy001.tenant.security.TenantPermission).TEN_USER_READ.asAuthority())")
    public ResponseEntity<TenantUserDetailsResponse> getTenantUser(@PathVariable Long userId) {
        log.info("Recebida requisição para buscar usuário do tenant. userId={}", userId);

        TenantUser user = tenantUserQueryService.getTenantUser(userId);
        TenantUserDetailsResponse response = tenantUserApiMapper.toDetails(user);

        log.info("Usuário do tenant carregado com sucesso. userId={}", userId);

        return ResponseEntity.ok(response);
    }

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

        validateStatusRequest(suspendedByAccount, suspendedByAdmin);

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

    /**
     * Valida a combinação de parâmetros do endpoint de status.
     *
     * @param suspendedByAccount flag de suspensão por conta
     * @param suspendedByAdmin flag de suspensão por admin
     */
    private void validateStatusRequest(Boolean suspendedByAccount, Boolean suspendedByAdmin) {
        if (suspendedByAccount == null && suspendedByAdmin == null) {
            throw new ApiException(
                    ApiErrorCode.INVALID_STATUS,
                    "Informe suspendedByAccount ou suspendedByAdmin",
                    400
            );
        }

        if (suspendedByAccount != null && suspendedByAdmin != null) {
            throw new ApiException(
                    ApiErrorCode.INVALID_STATUS,
                    "Informe apenas um dos parâmetros (suspendedByAccount OU suspendedByAdmin)",
                    400
            );
        }
    }
}