package brito.com.multitenancy001.tenant.me.app;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.integration.security.TenantRequestIdentityService;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.me.api.dto.TenantChangeMyPasswordRequest;
import brito.com.multitenancy001.tenant.me.api.dto.UpdateMyProfileRequest;
import brito.com.multitenancy001.tenant.users.app.command.TenantUserCommandService;
import brito.com.multitenancy001.tenant.users.app.query.TenantUserQueryService;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Application Service (Tenant): operações do próprio usuário autenticado ("me").
 *
 * <p><b>Responsabilidades:</b></p>
 * <ul>
 *   <li>Resolver accountId, tenantSchema e userId a partir da identidade do request.</li>
 *   <li>Consultar o perfil do usuário autenticado.</li>
 *   <li>Atualizar o próprio perfil.</li>
 *   <li>Trocar a própria senha.</li>
 * </ul>
 *
 * <p><b>Regra arquitetural importante:</b></p>
 * <ul>
 *   <li>Este service é chamado em rotas tenant autenticadas.</li>
 *   <li>Nesse cenário, o {@code TenantContext} já foi bindado pelo filtro JWT.</li>
 *   <li>Portanto, este service NÃO deve fazer re-bind do tenant via executor,
 *       evitando erro de contexto duplicado no mesmo thread.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantMeService {

    private final TenantUserQueryService tenantUserQueryService;
    private final TenantUserCommandService tenantUserCommandService;
    private final TenantRequestIdentityService requestIdentity;
    private final AppClock appClock;

    /**
     * Retorna o perfil do usuário autenticado no tenant atual.
     *
     * @return usuário autenticado
     */
    public TenantUser getMyProfile() {
        IdentitySnapshot identity = requireCurrentIdentity();

        log.info("Consultando perfil do usuário autenticado. accountId={}, userId={}, tenantSchema={}",
                identity.accountId(), identity.userId(), identity.tenantSchema());

        TenantUser result = tenantUserQueryService.getUser(identity.userId(), identity.accountId());

        log.info("Perfil do usuário autenticado carregado com sucesso. accountId={}, userId={}, tenantSchema={}",
                identity.accountId(), identity.userId(), identity.tenantSchema());

        return result;
    }

    /**
     * Atualiza o perfil do usuário autenticado.
     *
     * <p>Este método atualiza apenas dados de perfil, sem alterar role ou permissões.</p>
     *
     * @param req request de atualização
     * @return usuário atualizado
     */
    public TenantUser updateMyProfile(UpdateMyProfileRequest req) {
        if (req == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "request é obrigatório", 400);
        }

        IdentitySnapshot identity = requireCurrentIdentity();

        log.info("Atualizando perfil do usuário autenticado. accountId={}, userId={}, tenantSchema={}",
                identity.accountId(), identity.userId(), identity.tenantSchema());

        TenantUser result = tenantUserCommandService.updateProfile(
                identity.userId(),
                identity.accountId(),
                identity.tenantSchema(),
                req.name(),
                req.phone(),
                req.avatarUrl(),
                req.locale(),
                req.timezone(),
                appClock.instant()
        );

        log.info("Perfil do usuário autenticado atualizado com sucesso. accountId={}, userId={}, tenantSchema={}",
                identity.accountId(), identity.userId(), identity.tenantSchema());

        return result;
    }

    /**
     * Troca a senha do usuário autenticado.
     *
     * @param req request de troca de senha
     */
    public void changeMyPassword(TenantChangeMyPasswordRequest req) {
        if (req == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "request é obrigatório", 400);
        }

        IdentitySnapshot identity = requireCurrentIdentity();

        log.info("Iniciando troca de senha do usuário autenticado. accountId={}, userId={}, tenantSchema={}",
                identity.accountId(), identity.userId(), identity.tenantSchema());

        tenantUserCommandService.changeMyPassword(
                identity.userId(),
                identity.accountId(),
                identity.tenantSchema(),
                req.currentPassword(),
                req.newPassword(),
                req.confirmNewPassword()
        );

        log.info("Troca de senha do usuário autenticado concluída com sucesso. accountId={}, userId={}, tenantSchema={}",
                identity.accountId(), identity.userId(), identity.tenantSchema());
    }

    /**
     * Resolve e valida a identidade atual do request tenant autenticado.
     *
     * @return snapshot validado da identidade atual
     */
    private IdentitySnapshot requireCurrentIdentity() {
        Long accountId = requestIdentity.getCurrentAccountId();
        String tenantSchema = requestIdentity.getCurrentTenantSchema();
        Long userId = requestIdentity.getCurrentUserId();

        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);
        }

        if (!StringUtils.hasText(tenantSchema)) {
            throw new ApiException(ApiErrorCode.TENANT_CONTEXT_REQUIRED, "tenantSchema é obrigatório", 400);
        }

        if (userId == null) {
            throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatório", 400);
        }

        return new IdentitySnapshot(accountId, tenantSchema.trim(), userId);
    }

    /**
     * Snapshot interno da identidade autenticada no request.
     *
     * @param accountId id da conta
     * @param tenantSchema schema do tenant
     * @param userId id do usuário autenticado
     */
    private record IdentitySnapshot(Long accountId, String tenantSchema, Long userId) {
    }
}