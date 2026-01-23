package brito.com.multitenancy001.shared.account;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.persistence.account.AccountRepository;
import brito.com.multitenancy001.controlplane.persistence.account.AccountResolverProjection;
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
     * Resolve conta no schema PUBLIC e valida se existe e se está operacional.
     * Retorna apenas snapshot mínimo (sem expor a entidade do ControlPlane).
     */
    public AccountSnapshot resolveActiveAccountBySlug(String slug) {
        return publicExecutor.run(() -> {
            LocalDateTime now = appClock.now();

            AccountResolverProjection p = accountRepository.findProjectionBySlugAndDeletedFalse(slug)
                    .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));

            if (!isOperational(p, now)) {
                throw new ApiException("ACCOUNT_INACTIVE", "Conta inativa", 403);
            }

            return new AccountSnapshot(p.getId(), p.getSchemaName(), p.getStatus());
        });
    }

    private boolean isOperational(AccountResolverProjection p, LocalDateTime now) {
        if (p == null) return false;

        // BUILT_IN sempre operacional
        if ("BUILT_IN".equalsIgnoreCase(p.getOrigin())) return true;

        String status = p.getStatus();
        if ("ACTIVE".equalsIgnoreCase(status)) return true;

        if ("FREE_TRIAL".equalsIgnoreCase(status)) {
            return p.getTrialEndDate() != null && now != null && p.getTrialEndDate().isAfter(now);
        }

        return false;
    }
}
