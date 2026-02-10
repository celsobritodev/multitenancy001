package brito.com.multitenancy001.tenant.users.app;

import java.util.List;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.shared.contracts.UserSummaryData;
import lombok.RequiredArgsConstructor;

/**
 * Tenant APP: regras e operações administrativas em massa/conta.
 *
 * Importante:
 * - Fica no Tenant (app), porque as regras (ex.: não suspender OWNER) pertencem ao Tenant.
 * - Integração apenas chama isso dentro do schema certo.
 *
 * Implemente aqui usando seus repos/services atuais.
 */
@Service
@RequiredArgsConstructor
public class TenantUserAdminTxService {

    // TODO: injete aqui o que você já usa hoje para fazer essas operações
    // ex: TenantUserRepository, TenantUserService, etc.

    public int suspendAllUsersByAccount(Long accountId) {
        // TODO: mover a lógica que hoje está na TenantUserProvisioningFacade para cá
        throw new UnsupportedOperationException("NOT_IMPLEMENTED");
    }

    public int unsuspendAllUsersByAccount(Long accountId) {
        throw new UnsupportedOperationException("NOT_IMPLEMENTED");
    }

    public int softDeleteAllUsersByAccount(Long accountId) {
        throw new UnsupportedOperationException("NOT_IMPLEMENTED");
    }

    public int restoreAllUsersByAccount(Long accountId) {
        throw new UnsupportedOperationException("NOT_IMPLEMENTED");
    }

    public List<UserSummaryData> listUserSummaries(Long accountId, boolean onlyOperational) {
        throw new UnsupportedOperationException("NOT_IMPLEMENTED");
    }

    public void setSuspendedByAdmin(Long accountId, Long userId, boolean suspended) {
        throw new UnsupportedOperationException("NOT_IMPLEMENTED");
    }
}
