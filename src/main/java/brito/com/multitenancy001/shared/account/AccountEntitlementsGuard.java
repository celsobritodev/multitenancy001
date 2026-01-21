package brito.com.multitenancy001.shared.account;

import brito.com.multitenancy001.controlplane.domain.account.Account;
import brito.com.multitenancy001.controlplane.persistence.account.AccountRepository;
import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.shared.executor.PublicExecutor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountEntitlementsGuard {

    private final AccountRepository accountRepository;
    private final AccountEntitlementsService accountEntitlementsService;
    private final PublicExecutor publicExecutor;

    @Transactional(readOnly = true, transactionManager = "publicTransactionManager")
    public void assertCanCreateUser(Long accountId, long currentUsers) {
        if (accountId == null) {
            throw new ApiException("ACCOUNT_REQUIRED", "AccountId obrigatório", 400);
        }

        publicExecutor.run(() -> {
            Account account = accountRepository.findByIdAndDeletedFalse(accountId)
                    .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));

            accountEntitlementsService.assertCanCreateUser(account, currentUsers);
        });
    }
}
