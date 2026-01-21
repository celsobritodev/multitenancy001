package brito.com.multitenancy001.shared.account;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.domain.account.Account;
import brito.com.multitenancy001.controlplane.persistence.account.AccountRepository;
import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.shared.executor.PublicExecutor;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountResolver {

    private final AccountRepository accountRepository;
    private final AppClock appClock;
    private final PublicExecutor publicExecutor;

    /**
     * Resolve conta no schema PUBLIC e já valida se existe e se está ativa.
     * Retorna apenas um snapshot mínimo (sem expor o domínio do controlplane).
     */
    public AccountSnapshot resolveActiveAccountBySlug(String slug) {
        return publicExecutor.run(() -> {
            LocalDateTime now = appClock.now();

            Account account = accountRepository.findBySlugAndDeletedFalse(slug)
                    .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));

            if (!account.isOperational(now)) {
                throw new ApiException("ACCOUNT_INACTIVE", "Conta inativa", 403);
            }

            return new AccountSnapshot(account.getId(), account.getSchemaName(), account.getStatus().name());
        });
    }
}
