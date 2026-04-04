package brito.com.multitenancy001.controlplane.users.app;

import java.util.List;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneMeResponse;
import brito.com.multitenancy001.controlplane.users.api.dto.ControlPlaneUserDetailsResponse;
import brito.com.multitenancy001.controlplane.users.domain.ControlPlaneUser;
import brito.com.multitenancy001.controlplane.users.persistence.ControlPlaneUserRepository;
import brito.com.multitenancy001.integration.security.ControlPlaneRequestIdentityService;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de leitura de usuários do Control Plane.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Listar usuários.</li>
 *   <li>Buscar usuário por id.</li>
 *   <li>Listar e buscar usuários habilitados.</li>
 *   <li>Montar resposta do endpoint {@code /api/controlplane/me}.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ControlPlaneUserQueryService {

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final ControlPlaneUserRepository controlPlaneUserRepository;
    private final ControlPlaneRequestIdentityService controlPlaneRequestIdentityService;
    private final ControlPlaneUserInternalFacade controlPlaneUserInternalFacade;

    /**
     * Lista usuários não deletados do Control Plane.
     *
     * @return lista de usuários
     */
    public List<ControlPlaneUserDetailsResponse> listControlPlaneUsers() {
        log.debug("listControlPlaneUsers chamado");
        return publicSchemaUnitOfWork.readOnly(() -> {
            Account controlPlaneAccount = controlPlaneUserInternalFacade.getControlPlaneAccount();
            return controlPlaneUserRepository.findNotDeletedByAccountId(controlPlaneAccount.getId())
                    .stream()
                    .map(controlPlaneUserInternalFacade::mapToDetailsResponse)
                    .toList();
        });
    }

    /**
     * Obtém usuário do Control Plane por id.
     *
     * @param userId id do usuário
     * @return usuário encontrado
     */
    public ControlPlaneUserDetailsResponse getControlPlaneUser(Long userId) {
        log.debug("getControlPlaneUser chamado | userId={}", userId);

        return publicSchemaUnitOfWork.readOnly(() -> {
            if (userId == null) {
                throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatório", 400);
            }

            Account controlPlaneAccount = controlPlaneUserInternalFacade.getControlPlaneAccount();
            ControlPlaneUser user = controlPlaneUserInternalFacade.loadUserInControlPlane(userId, controlPlaneAccount.getId());

            return controlPlaneUserInternalFacade.mapToDetailsResponse(user);
        });
    }

    /**
     * Lista usuários habilitados do Control Plane.
     *
     * @return lista de usuários habilitados
     */
    public List<ControlPlaneUserDetailsResponse> listEnabledControlPlaneUsers() {
        log.debug("listEnabledControlPlaneUsers chamado");
        return publicSchemaUnitOfWork.readOnly(() -> {
            Account controlPlaneAccount = controlPlaneUserInternalFacade.getControlPlaneAccount();
            return controlPlaneUserRepository.findEnabledByAccountId(controlPlaneAccount.getId())
                    .stream()
                    .map(controlPlaneUserInternalFacade::mapToDetailsResponse)
                    .toList();
        });
    }

    /**
     * Obtém usuário habilitado do Control Plane por id.
     *
     * @param userId id do usuário
     * @return usuário habilitado
     */
    public ControlPlaneUserDetailsResponse getEnabledControlPlaneUser(Long userId) {
        log.debug("getEnabledControlPlaneUser chamado | userId={}", userId);

        return publicSchemaUnitOfWork.readOnly(() -> {
            if (userId == null) {
                throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatório", 400);
            }

            Account controlPlaneAccount = controlPlaneUserInternalFacade.getControlPlaneAccount();
            ControlPlaneUser user =
                    controlPlaneUserInternalFacade.loadEnabledUserInControlPlane(userId, controlPlaneAccount.getId());

            return controlPlaneUserInternalFacade.mapToDetailsResponse(user);
        });
    }

    /**
     * Obtém dados do próprio usuário autenticado do Control Plane.
     *
     * @return dados do usuário autenticado
     */
    public ControlPlaneMeResponse getMe() {
        log.debug("getMe chamado");

        return publicSchemaUnitOfWork.readOnly(() -> {
            Long currentUserId = controlPlaneRequestIdentityService.getCurrentUserId();

            Account controlPlaneAccount = controlPlaneUserInternalFacade.getControlPlaneAccount();
            ControlPlaneUser user = controlPlaneUserRepository.findById(currentUserId)
                    .orElseThrow(() -> new ApiException(
                            ApiErrorCode.USER_NOT_FOUND,
                            "Usuário não encontrado",
                            404
                    ));

            if (user.getAccount() == null
                    || user.getAccount().getId() == null
                    || !controlPlaneAccount.getId().equals(user.getAccount().getId())) {
                throw new ApiException(
                        ApiErrorCode.FORBIDDEN,
                        "Usuário não pertence ao Control Plane",
                        403
                );
            }

            return controlPlaneUserInternalFacade.mapToMeResponse(user);
        });
    }
}