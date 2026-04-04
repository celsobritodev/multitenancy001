package brito.com.multitenancy001.tenant.users.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import brito.com.multitenancy001.tenant.users.api.dto.TenantUserDetailsResponse;
import brito.com.multitenancy001.tenant.users.api.dto.TenantUserListItemResponse;
import brito.com.multitenancy001.tenant.users.api.dto.TenantUserSummaryResponse;
import brito.com.multitenancy001.tenant.users.api.dto.TenantUsersListResponse;
import brito.com.multitenancy001.tenant.users.api.mapper.TenantUserApiMapper;
import brito.com.multitenancy001.tenant.users.app.context.TenantUserCurrentContextQueryService;
import brito.com.multitenancy001.tenant.users.app.query.TenantUsersListView;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Controller REST responsável pelos endpoints de leitura do módulo de usuários tenant.
 *
 * <p><b>Responsabilidades:</b></p>
 * <ul>
 *   <li>Receber requisições HTTP de consulta de usuários do tenant atual.</li>
 *   <li>Delegar leituras ao query service do contexto corrente.</li>
 *   <li>Mapear entidades de domínio para DTOs de resposta.</li>
 *   <li>Preservar rotas oficiais e rotas legadas exigidas pelas suítes E2E.</li>
 * </ul>
 *
 * <p><b>Diretrizes arquiteturais:</b></p>
 * <ul>
 *   <li>Não implementa regra de negócio.</li>
 *   <li>Não acessa repositórios diretamente.</li>
 *   <li>Não controla transações diretamente.</li>
 *   <li>Usa regex numérica em path variables para evitar captura indevida.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/tenant/users")
@RequiredArgsConstructor
@Validated
@Slf4j
public class TenantUserQueryController {

    private final TenantUserCurrentContextQueryService tenantUserQueryService;
    private final TenantUserApiMapper tenantUserApiMapper;

    /**
     * Lista usuários do tenant corrente.
     *
     * <p>Quando o solicitante é owner, a resposta pode ser enriquecida
     * com dados adicionais e permissões. Caso contrário, retorna a visão básica.</p>
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
     * Endpoint legado de contagem de usuários habilitados.
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
}