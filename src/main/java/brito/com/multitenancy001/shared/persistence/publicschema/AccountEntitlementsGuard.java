package brito.com.multitenancy001.shared.persistence.publicschema;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountEntitlementsGuard {

    private final AccountRepository accountRepository;
    private final AccountEntitlementsService accountEntitlementsService;
    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;

    public void assertCanCreateUser(Long accountId, long currentUsers) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "AccountId obrigatório", 400);
        }

        // ✅ PRECISA ser TX normal, porque pode provisionar entitlements
        publicSchemaUnitOfWork.tx(() -> {
            Account account = accountRepository.findByIdAndDeletedFalse(accountId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.ACCOUNT_NOT_FOUND, "Conta não encontrada", 404));

            accountEntitlementsService.assertCanCreateUser(account, currentUsers);
        });
    }
}

