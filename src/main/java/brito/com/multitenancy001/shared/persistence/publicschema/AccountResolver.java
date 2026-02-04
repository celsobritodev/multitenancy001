package brito.com.multitenancy001.shared.persistence.publicschema;

import java.time.Instant;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountResolverProjection;
import brito.com.multitenancy001.shared.executor.PublicExecutor;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountResolver {

    private final AccountRepository accountRepository;
    private final AppClock appClock;
    private final PublicExecutor publicExecutor;

    public AccountSnapshot resolveActiveAccountBySlug(String slug) {
        return publicExecutor.run(() -> {
            Instant now = appClock.instant();

            AccountResolverProjection p = accountRepository.findProjectionBySlugAndDeletedFalseIgnoreCase(slug)
                    .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));

            if (!isOperational(p, now)) {
                throw new ApiException("ACCOUNT_INACTIVE", "Conta inativa", 403);
            }

            return new AccountSnapshot(p.getId(), p.getSchemaName(), p.getSlug(), p.getDisplayName());
        });
    }

    public AccountSnapshot resolveActiveAccountById(Long accountId) {
        if (accountId == null) throw new ApiException("INVALID_ACCOUNT", "accountId inválido", 400);
        return resolveActiveAccountByIdInternal(accountId);
    }

    private AccountSnapshot resolveActiveAccountByIdInternal(Long accountId) {
        return publicExecutor.run(() -> {
            Instant now = appClock.instant();

            AccountResolverProjection p = accountRepository.findProjectionByIdAndDeletedFalse(accountId)
                    .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));

            if (!isOperational(p, now)) {
                throw new ApiException("ACCOUNT_INACTIVE", "Conta inativa", 403);
            }

            return new AccountSnapshot(p.getId(), p.getSchemaName(), p.getSlug(), p.getDisplayName());
        });
    }

    private boolean isOperational(AccountResolverProjection p, Instant now) {
        if (p == null) return false;

        if ("BUILT_IN".equalsIgnoreCase(p.getOrigin())) return true;

        String status = p.getStatus();
        if ("ACTIVE".equalsIgnoreCase(status)) return true;

        if ("FREE_TRIAL".equalsIgnoreCase(status)) {
            return p.getTrialEndAt() != null && now != null && p.getTrialEndAt().isAfter(now);
        }

        return false;
    }

}

