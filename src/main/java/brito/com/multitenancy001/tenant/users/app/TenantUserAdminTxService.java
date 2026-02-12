package brito.com.multitenancy001.tenant.users.app;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import brito.com.multitenancy001.infrastructure.persistence.TxExecutor;
import brito.com.multitenancy001.shared.contracts.UserSummaryData;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.security.TenantRoleName;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.security.TenantRole;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import brito.com.multitenancy001.tenant.users.persistence.TenantUserRepository;
import lombok.RequiredArgsConstructor;

/**
 * Tenant APP: regras e operações administrativas em massa/conta.
 *
 * Importante:
 * - Fica no Tenant (app), porque as regras (ex.: não suspender OWNER) pertencem ao Tenant.
 * - Integração apenas chama isso dentro do schema certo.
 *
 * Pré-condição: este serviço é chamado já dentro do schema do tenant (via TenantExecutor/TenantExecutor).
 */
@Service
@RequiredArgsConstructor
public class TenantUserAdminTxService {

    private static final String TENANT_OWNER_REQUIRED = "TENANT_OWNER_REQUIRED";

    private final TxExecutor transactionExecutor;
    private final TenantUserRepository tenantUserRepository;
    private final AppClock appClock;

    /**
     * ✅ (SAFE) Admin bulk: suspende todos MENOS TENANT_OWNER.
     * Regra: precisa existir ao menos 1 TENANT_OWNER não deletado (estado mínimo válido).
     */
    public int suspendAllUsersByAccount(Long accountId) {
        return transactionExecutor.inTenantRequiresNew(() -> {
            requireAccountId(accountId);

            long ownersNotDeleted = tenantUserRepository.countNotDeletedByAccountIdAndRole(accountId, TenantRole.TENANT_OWNER);
            if (ownersNotDeleted <= 0) {
                throw new ApiException(
                        TENANT_OWNER_REQUIRED,
                        "Não é possível suspender usuários: não existe TENANT_OWNER não deletado para esta conta (estado inválido).",
                        409
                );
            }

            return tenantUserRepository.suspendAllByAccountExceptRole(accountId, TenantRole.TENANT_OWNER);
        });
    }

    /**
     * ✅ Reativa todos (inclusive owners).
     */
    public int unsuspendAllUsersByAccount(Long accountId) {
        return transactionExecutor.inTenantRequiresNew(() -> {
            requireAccountId(accountId);
            return tenantUserRepository.unsuspendAllByAccount(accountId);
        });
    }

    /**
     * ✅ (SAFE) Cancelamento / exclusão da conta:
     * soft-delete de todos os usuários MENOS TENANT_OWNER.
     * Regra: precisa existir ao menos 1 TENANT_OWNER não deletado (estado mínimo válido).
     */
    public int softDeleteAllUsersByAccount(Long accountId) {
        return transactionExecutor.inTenantRequiresNew(() -> {
            requireAccountId(accountId);

            long ownersNotDeleted = tenantUserRepository.countNotDeletedByAccountIdAndRole(accountId, TenantRole.TENANT_OWNER);
            if (ownersNotDeleted <= 0) {
                throw new ApiException(
                        TENANT_OWNER_REQUIRED,
                        "Não é possível remover usuários: não existe TENANT_OWNER não deletado para esta conta (estado inválido).",
                        409
                );
            }

            Instant now = appClock.instant();
            return tenantUserRepository.softDeleteAllByAccountExceptRole(accountId, TenantRole.TENANT_OWNER, now);
        });
    }

    public int restoreAllUsersByAccount(Long accountId) {
        return transactionExecutor.inTenantRequiresNew(() -> {
            requireAccountId(accountId);
            return tenantUserRepository.restoreAllByAccount(accountId);
        });
    }

    /**
     * Retorna um summary “contratual” para o Control Plane / integrações.
     *
     * onlyOperational=true:
     *   deleted=false AND suspendedByAccount=false AND suspendedByAdmin=false
     */
    public List<UserSummaryData> listUserSummaries(Long accountId, boolean onlyOperational) {
        return transactionExecutor.inTenantReadOnlyTx(() -> {
            requireAccountId(accountId);

            List<TenantUser> users = onlyOperational
                    ? tenantUserRepository.findEnabledUsersByAccount(accountId)
                    : tenantUserRepository.findByAccountId(accountId);

            return users.stream()
                    .map(this::toSummary)
                    .toList();
        });
    }

    /**
     * Suspensão manual por admin em um único usuário.
     *
     * Regra crítica:
     * - não permitir suspender o ÚLTIMO TENANT_OWNER ativo.
     */
    public void setSuspendedByAdmin(Long accountId, Long userId, boolean suspended) {
        transactionExecutor.inTenantTx(() -> {
            requireAccountId(accountId);
            requireUserId(userId);

            if (suspended) {
                TenantUser user = tenantUserRepository.findByIdAndAccountIdAndDeletedFalse(userId, accountId)
                        .orElseThrow(() -> new ApiException("USER_NOT_FOUND", "Usuário não encontrado ou removido", 404));

                if (isActiveOwner(user)) {
                    long activeOwners = tenantUserRepository.countActiveOwnersByAccountId(accountId, TenantRole.TENANT_OWNER);
                    if (activeOwners <= 1) {
                        throw new ApiException(
                                TENANT_OWNER_REQUIRED,
                                "Não é permitido suspender o último TENANT_OWNER ativo.",
                                409
                        );
                    }
                }
            }

            int updated = tenantUserRepository.setSuspendedByAdmin(accountId, userId, suspended);
            if (updated == 0) {
                throw new ApiException("USER_NOT_FOUND", "Usuário não encontrado ou removido", 404);
            }
        });
    }

    // =========================================================
    // Helpers
    // =========================================================

    private UserSummaryData toSummary(TenantUser u) {
        return new UserSummaryData(
                u.getId(),
                u.getAccountId(),
                u.getName(),
                u.getEmail(),
                u.getRole() == null ? null : TenantRoleName.valueOf(u.getRole().name()),
                u.isSuspendedByAccount(),
                u.isSuspendedByAdmin(),
                u.isDeleted()
        );
    }

    private boolean isActiveOwner(TenantUser user) {
        if (user == null) return false;
        if (user.isDeleted()) return false;
        if (user.isSuspendedByAccount()) return false;
        if (user.isSuspendedByAdmin()) return false;
        return user.getRole() != null && user.getRole().isTenantOwner();
    }

    private void requireAccountId(Long accountId) {
        if (accountId == null) {
            throw new ApiException("ACCOUNT_REQUIRED", "AccountId obrigatório", 400);
        }
    }

    private void requireUserId(Long userId) {
        if (userId == null) {
            throw new ApiException("USER_ID_REQUIRED", "userId obrigatório", 400);
        }
    }
}
