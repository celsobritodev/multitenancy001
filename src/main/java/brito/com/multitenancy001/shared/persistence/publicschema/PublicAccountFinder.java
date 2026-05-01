package brito.com.multitenancy001.shared.persistence.publicschema;

import java.time.Instant;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountSummary;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.executor.PublicSchemaExecutor;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.shared.validation.RequiredValidator;
import lombok.RequiredArgsConstructor;

/**
 * Finder público de contas para fluxos compartilhados.
 */
@Service
@RequiredArgsConstructor
public class PublicAccountFinder {

    private final AccountRepository accountRepository;
    private final AppClock appClock;
    private final PublicSchemaExecutor publicExecutor;

    public PublicAccountView resolveActiveAccountBySlug(String slug) {
        return publicExecutor.inPublic(() -> {
            Instant now = appClock.instant();

            AccountSummary accountSummary = accountRepository.findProjectionBySlugAndDeletedFalseIgnoreCase(slug)
                    .orElseThrow(() -> new ApiException(
                            ApiErrorCode.ACCOUNT_NOT_FOUND,
                            "Conta não encontrada"
                    ));

            if (!isOperational(accountSummary, now)) {
                throw new ApiException(
                        ApiErrorCode.ACCOUNT_INACTIVE,
                        "Conta inativa"
                );
            }

            return new PublicAccountView(
                    accountSummary.getId(),
                    accountSummary.getTenantSchema(),
                    accountSummary.getSlug(),
                    accountSummary.getDisplayName()
            );
        });
    }

    public PublicAccountView resolveActiveAccountById(Long accountId) {
        RequiredValidator.requirePayload(
                accountId,
                ApiErrorCode.INVALID_ACCOUNT,
                "accountId inválido"
        );

        return resolveActiveAccountByIdInternal(accountId);
    }

    private PublicAccountView resolveActiveAccountByIdInternal(Long accountId) {
        return publicExecutor.inPublic(() -> {
            Instant now = appClock.instant();

            AccountSummary accountSummary = accountRepository.findProjectionByIdAndDeletedFalse(accountId)
                    .orElseThrow(() -> new ApiException(
                            ApiErrorCode.ACCOUNT_NOT_FOUND,
                            "Conta não encontrada"
                    ));

            if (!isOperational(accountSummary, now)) {
                throw new ApiException(
                        ApiErrorCode.ACCOUNT_INACTIVE,
                        "Conta inativa"
                );
            }

            return new PublicAccountView(
                    accountSummary.getId(),
                    accountSummary.getTenantSchema(),
                    accountSummary.getSlug(),
                    accountSummary.getDisplayName()
            );
        });
    }

    private boolean isOperational(AccountSummary accountSummary, Instant now) {
        if (accountSummary == null) {
            return false;
        }

        if ("BUILT_IN".equalsIgnoreCase(accountSummary.getOrigin())) {
            return true;
        }

        String status = accountSummary.getStatus();

        if ("ACTIVE".equalsIgnoreCase(status)) {
            return true;
        }

        if ("FREE_TRIAL".equalsIgnoreCase(status)) {
            return accountSummary.getTrialEndAt() != null
                    && now != null
                    && accountSummary.getTrialEndAt().isAfter(now);
        }

        return false;
    }
}