package brito.com.multitenancy001.tenant.users.app.query;

import brito.com.multitenancy001.infrastructure.persistence.TxExecutor;
import brito.com.multitenancy001.shared.account.UserLimitPolicy;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.domain.EmailNormalizer;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.validation.ValidationPatterns;
import brito.com.multitenancy001.tenant.security.TenantRole;
import brito.com.multitenancy001.tenant.users.domain.TenantUser;
import brito.com.multitenancy001.tenant.users.persistence.TenantUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TenantUserQueryService {

    private final TenantUserRepository tenantUserRepository;
    private final TxExecutor transactionExecutor;

    // =========================================================
    // LIMITS / COUNTS
    // =========================================================

    public long countUsersForLimit(Long accountId, UserLimitPolicy policy) {
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", ApiErrorCode.ACCOUNT_REQUIRED.defaultHttpStatus());

        return transactionExecutor.inTenantReadOnlyTx(() -> {
            if (policy == null) return tenantUserRepository.countByAccountIdAndDeletedFalse(accountId);

            return switch (policy) {
                case SEATS_IN_USE -> tenantUserRepository.countByAccountIdAndDeletedFalse(accountId);
                case SEATS_ENABLED -> tenantUserRepository.countEnabledUsersByAccount(accountId);
                default -> tenantUserRepository.countByAccountIdAndDeletedFalse(accountId);
            };
        });
    }

    public long countEnabledUsersByAccount(Long accountId) {
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", ApiErrorCode.ACCOUNT_REQUIRED.defaultHttpStatus());
        return transactionExecutor.inTenantReadOnlyTx(() -> tenantUserRepository.countEnabledUsersByAccount(accountId));
    }

    public long countActiveOwnersByAccountId(Long accountId, TenantRole ownerRole) {
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", ApiErrorCode.ACCOUNT_REQUIRED.defaultHttpStatus());
        if (ownerRole == null) throw new ApiException(ApiErrorCode.ROLE_REQUIRED, "role é obrigatório", ApiErrorCode.ROLE_REQUIRED.defaultHttpStatus());

        return transactionExecutor.inTenantReadOnlyTx(() ->
                tenantUserRepository.countActiveOwnersByAccountId(accountId, ownerRole)
        );
    }

    // =========================================================
    // READ / LIST
    // =========================================================

    public TenantUser getUser(Long userId, Long accountId) {
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", ApiErrorCode.ACCOUNT_REQUIRED.defaultHttpStatus());
        if (userId == null) throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatório", ApiErrorCode.USER_ID_REQUIRED.defaultHttpStatus());

        return transactionExecutor.inTenantReadOnlyTx(() ->
                tenantUserRepository.findByIdAndAccountIdAndDeletedFalse(userId, accountId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário não encontrado", ApiErrorCode.USER_NOT_FOUND.defaultHttpStatus()))
        );
    }

    public TenantUser getEnabledUser(Long userId, Long accountId) {
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", ApiErrorCode.ACCOUNT_REQUIRED.defaultHttpStatus());
        if (userId == null) throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatório", ApiErrorCode.USER_ID_REQUIRED.defaultHttpStatus());

        return transactionExecutor.inTenantReadOnlyTx(() ->
                tenantUserRepository.findEnabledByIdAndAccountId(userId, accountId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário habilitado não encontrado", ApiErrorCode.USER_NOT_FOUND.defaultHttpStatus()))
        );
    }

    public TenantUser getUserByEmail(String email, Long accountId) {
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", ApiErrorCode.ACCOUNT_REQUIRED.defaultHttpStatus());
        if (!StringUtils.hasText(email)) throw new ApiException(ApiErrorCode.INVALID_EMAIL, "Email é obrigatório", ApiErrorCode.INVALID_EMAIL.defaultHttpStatus());

        String normEmail = EmailNormalizer.normalizeOrNull(email);
        if (!StringUtils.hasText(normEmail) || !normEmail.matches(ValidationPatterns.EMAIL_PATTERN)) {
            throw new ApiException(ApiErrorCode.INVALID_EMAIL, "Email inválido", ApiErrorCode.INVALID_EMAIL.defaultHttpStatus());
        }

        return transactionExecutor.inTenantReadOnlyTx(() ->
                tenantUserRepository.findByEmailAndAccountIdAndDeletedFalse(normEmail, accountId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.USER_NOT_FOUND, "Usuário não encontrado", ApiErrorCode.USER_NOT_FOUND.defaultHttpStatus()))
        );
    }

    public List<TenantUser> listUsers(Long accountId) {
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", ApiErrorCode.ACCOUNT_REQUIRED.defaultHttpStatus());

        return transactionExecutor.inTenantReadOnlyTx(() ->
                tenantUserRepository.findByAccountIdAndDeletedFalse(accountId)
        );
    }

    public List<TenantUser> listEnabledUsers(Long accountId) {
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", ApiErrorCode.ACCOUNT_REQUIRED.defaultHttpStatus());

        return transactionExecutor.inTenantReadOnlyTx(() ->
                tenantUserRepository.findEnabledUsersByAccount(accountId)
        );
    }
}
