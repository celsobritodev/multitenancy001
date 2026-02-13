package brito.com.multitenancy001.tenant.users.app;

import java.util.List;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.infrastructure.persistence.TxExecutor;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.contracts.UserSummaryData;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.security.TenantRoleName;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import brito.com.multitenancy001.tenant.users.persistence.TenantUserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TenantUserAdminTxService {

    private final TxExecutor txExecutor;
    private final TenantUserRepository tenantUserRepository;

    // =========================================================
    // Admin Ops (TX)
    // =========================================================

    public int suspendAllUsersByAccount(Long accountId) {
        requireAccountId(accountId);

        return txExecutor.tenantTx(() ->
                tenantUserRepository.suspendAllByAccount(accountId)
        );
    }

    public int unsuspendAllUsersByAccount(Long accountId) {
        requireAccountId(accountId);

        return txExecutor.tenantTx(() ->
                tenantUserRepository.unsuspendAllByAccount(accountId)
        );
    }

    public List<UserSummaryData> listUsersByAccount(Long accountId) {
        requireAccountId(accountId);

        return txExecutor.tenantReadOnlyTx(() ->
                tenantUserRepository.findAllByAccountId(accountId)
                        .stream()
                        .map(this::toSummary)
                        .toList()
        );
    }

    public UserSummaryData getUser(Long accountId, Long userId) {
        requireAccountId(accountId);
        requireUserId(userId);

        return txExecutor.tenantReadOnlyTx(() -> {
            TenantUser u = tenantUserRepository.findByIdAndAccountId(userId, accountId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário não encontrado"));
            return toSummary(u);
        });
    }

    public void setSuspendedByAdmin(Long accountId, Long userId, boolean suspended) {
        requireAccountId(accountId);
        requireUserId(userId);

        txExecutor.tenantTx(() -> {
            TenantUser u = tenantUserRepository.findByIdAndAccountId(userId, accountId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário não encontrado"));

            // Regra simples (exemplo): não suspender OWNER ativo por admin
            if (suspended && isActiveOwner(u)) {
                throw new ApiException(ApiErrorCode.FORBIDDEN, "Não é permitido suspender o OWNER ativo");
            }

            int updated = tenantUserRepository.setSuspendedByAdmin(accountId, userId, suspended);
            if (updated == 0) {
                throw new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário não encontrado ou removido");
            }
        });
    }

    // =========================================================
    // Helpers
    // =========================================================

    private UserSummaryData toSummary(TenantUser u) {
        if (u == null) return null;

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
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "AccountId obrigatório");
        }
    }

    private void requireUserId(Long userId) {
        if (userId == null) {
            throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "UserId obrigatório");
        }
    }
}
