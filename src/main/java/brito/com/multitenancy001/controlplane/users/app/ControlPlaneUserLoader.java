package brito.com.multitenancy001.controlplane.users.app;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.users.domain.ControlPlaneUser;
import brito.com.multitenancy001.controlplane.users.persistence.ControlPlaneUserRepository;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;

/**
 * Serviço responsável pelo loading de usuários do Control Plane.
 */
@Service
@RequiredArgsConstructor
public class ControlPlaneUserLoader {

    private final ControlPlaneUserRepository controlPlaneUserRepository;

    /**
     * Carrega usuário não deletado no escopo do Control Plane.
     *
     * @param userId id do usuário
     * @param controlPlaneAccountId id da conta do Control Plane
     * @return usuário encontrado
     */
    public ControlPlaneUser loadNotDeletedUserInControlPlane(Long userId, Long controlPlaneAccountId) {
        return controlPlaneUserRepository
                .findNotDeletedByIdAndAccountId(userId, controlPlaneAccountId)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.USER_NOT_FOUND,
                        "Usuário não encontrado",
                        404
                ));
    }

    /**
     * Carrega usuário por id e valida escopo no Control Plane.
     *
     * @param userId id do usuário
     * @param controlPlaneAccountId id da conta do Control Plane
     * @return usuário encontrado
     */
    public ControlPlaneUser loadUserInControlPlane(Long userId, Long controlPlaneAccountId) {
        ControlPlaneUser user = controlPlaneUserRepository.findById(userId)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.USER_NOT_FOUND,
                        "Usuário não encontrado",
                        404
                ));

        if (user.getAccount() == null
                || user.getAccount().getId() == null
                || !user.getAccount().getId().equals(controlPlaneAccountId)) {
            throw new ApiException(
                    ApiErrorCode.USER_OUT_OF_SCOPE,
                    "Usuário não pertence ao Control Plane",
                    403
            );
        }

        return user;
    }

    /**
     * Carrega usuário habilitado por id no escopo do Control Plane.
     *
     * @param userId id do usuário
     * @param controlPlaneAccountId id da conta do Control Plane
     * @return usuário habilitado
     */
    public ControlPlaneUser loadEnabledUserInControlPlane(Long userId, Long controlPlaneAccountId) {
        return controlPlaneUserRepository
                .findEnabledByIdAndAccountId(userId, controlPlaneAccountId)
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.USER_NOT_ENABLED,
                        "Usuário não encontrado ou não habilitado",
                        404
                ));
    }
}