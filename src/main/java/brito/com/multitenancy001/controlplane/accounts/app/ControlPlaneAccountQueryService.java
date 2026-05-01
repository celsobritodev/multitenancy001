package brito.com.multitenancy001.controlplane.accounts.app;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.app.dto.AccountAdminDetailsProjection;
import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.AccountStatus;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.controlplane.users.domain.ControlPlaneUser;
import brito.com.multitenancy001.controlplane.users.persistence.ControlPlaneUserRepository;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.shared.validation.RequiredValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de consulta do agregado Account no Control Plane.
 *
 * <p>Regra V33:</p>
 * <ul>
 *   <li>Sem status HTTP hardcoded.</li>
 *   <li>Sem validação inline repetitiva.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ControlPlaneAccountQueryService {

    private final ControlPlaneUserRepository controlPlaneUserRepository;
    private final AccountRepository accountRepository;
    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final AppClock appClock;
    private final ControlPlaneAccountAdminQueryHelper controlPlaneAccountAdminQueryHelper;

    public List<Account> listAccounts() {
        log.debug("Listando contas não deletadas.");
        return publicSchemaUnitOfWork.readOnly(accountRepository::findAllByDeletedFalse);
    }

    public Account getAccount(Long accountId) {
        RequiredValidator.requireAccountId(accountId);

        return publicSchemaUnitOfWork.readOnly(() ->
                accountRepository.findByIdAndDeletedFalse(accountId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.ACCOUNT_NOT_FOUND))
        );
    }

    public AccountAdminDetailsProjection getAccountAdminDetails(Long accountId) {
        RequiredValidator.requireAccountId(accountId);

        return publicSchemaUnitOfWork.readOnly(() -> {

            Account account = accountRepository.findByIdAndDeletedFalse(accountId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.ACCOUNT_NOT_FOUND));

            long totalUsers = controlPlaneUserRepository.countByAccount_IdAndDeletedFalse(accountId);
            ControlPlaneUser admin = controlPlaneUserRepository.findFirstAdminByAccountId(accountId).orElse(null);

            return new AccountAdminDetailsProjection(account, admin, totalUsers);
        });
    }

    public Account findBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            throw new ApiException(
                    ApiErrorCode.INVALID_SLUG,
                    "slug é obrigatório"
            );
        }

        String normalized = slug.trim();

        return publicSchemaUnitOfWork.readOnly(() ->
                accountRepository.findBySlugAndDeletedFalseIgnoreCase(normalized)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.ACCOUNT_NOT_FOUND))
        );
    }

    public Page<Account> listAccountsByStatus(AccountStatus status, Pageable pageable) {
        if (status == null) {
            throw new ApiException(
                    ApiErrorCode.STATUS_REQUIRED,
                    "status é obrigatório"
            );
        }

        Pageable normalizedPageable = controlPlaneAccountAdminQueryHelper.normalizePageable(pageable);

        return publicSchemaUnitOfWork.readOnly(() ->
                accountRepository.findByStatusAndDeletedFalse(status, normalizedPageable)
        );
    }

    public Page<Account> listAccountsCreatedBetween(Instant start, Instant end, Pageable pageable) {
        controlPlaneAccountAdminQueryHelper.assertValidCreatedBetweenRange(start, end);

        Pageable normalizedPageable = controlPlaneAccountAdminQueryHelper.normalizePageable(pageable);

        return publicSchemaUnitOfWork.readOnly(() ->
                accountRepository.findAccountsCreatedBetween(start, end, normalizedPageable)
        );
    }

    public Page<Account> searchAccountsByDisplayName(String term, Pageable pageable) {
        if (term == null || term.isBlank()) {
            throw new ApiException(
                    ApiErrorCode.INVALID_SEARCH,
                    "term é obrigatório"
            );
        }

        String normalized = term.trim();
        Pageable normalizedPageable = controlPlaneAccountAdminQueryHelper.normalizePageable(pageable);

        return publicSchemaUnitOfWork.readOnly(() ->
                accountRepository.searchByDisplayName(normalized, normalizedPageable)
        );
    }

    public List<Account> listOverdueAccounts(Instant today, AccountStatus status) {

        Instant baseInstant = (today != null ? today : appClock.instant());
        LocalDate baseDateUtc = LocalDate.ofInstant(baseInstant, ZoneOffset.UTC);

        AccountStatus effectiveStatus = (status != null ? status : AccountStatus.ACTIVE);

        return publicSchemaUnitOfWork.readOnly(() ->
                accountRepository.findOverdueAccountsNotDeleted(effectiveStatus, baseDateUtc)
        );
    }

    public long countOperationalAccounts() {
        return publicSchemaUnitOfWork.readOnly(accountRepository::countOperationalAccounts);
    }
}