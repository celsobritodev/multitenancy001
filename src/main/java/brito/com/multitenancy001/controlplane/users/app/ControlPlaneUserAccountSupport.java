package brito.com.multitenancy001.controlplane.users.app;

import org.springframework.stereotype.Component;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;

/**
 * Componente responsável por helpers de account do módulo de usuários do Control Plane.
 */
@Component
@RequiredArgsConstructor
public class ControlPlaneUserAccountSupport {

    private final AccountRepository accountRepository;

    /**
     * Obtém a conta única do Control Plane.
     *
     * @return conta do Control Plane
     */
    public Account getControlPlaneAccount() {
        try {
            return accountRepository.getSingleControlPlaneAccount();
        } catch (IllegalStateException ex) {
            throw new ApiException(
                    ApiErrorCode.CONTROLPLANE_ACCOUNT_INVALID,
                    ControlPlaneUserSupport.MSG_CP_ACCOUNT_INVALID + " " + ex.getMessage(),
                    500
            );
        }
    }
}