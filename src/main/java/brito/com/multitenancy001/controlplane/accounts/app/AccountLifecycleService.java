package brito.com.multitenancy001.controlplane.accounts.app;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.app.command.AccountStatusChangeCommand;
import brito.com.multitenancy001.controlplane.accounts.app.dto.AccountAdminDetailsProjection;
import brito.com.multitenancy001.controlplane.accounts.app.dto.AccountStatusChangeResult;
import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.AccountStatus;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.controlplane.signup.app.AccountOnboardingService;
import brito.com.multitenancy001.controlplane.signup.app.command.SignupCommand;
import brito.com.multitenancy001.controlplane.signup.app.dto.SignupResult;
import brito.com.multitenancy001.controlplane.users.domain.ControlPlaneUser;
import brito.com.multitenancy001.controlplane.users.persistence.ControlPlaneUserRepository;
import brito.com.multitenancy001.shared.contracts.UserSummaryData;
import brito.com.multitenancy001.shared.executor.PublicUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountLifecycleService {

    private final ControlPlaneUserRepository controlPlaneUserRepository;
    private final AccountRepository accountRepository;
    private final AccountOnboardingService accountOnboardingService;
    private final AccountStatusService accountStatusService;
    private final AccountTenantUserService accountTenantUserService;
    private final PublicUnitOfWork publicUnitOfWork;
    private final AppClock appClock;

    // 1) ONBOARDING / SIGNUP
    public SignupResult createAccount(SignupCommand signupCommand) {
        return accountOnboardingService.createAccount(signupCommand);
    }

    // 2) CONSULTAS
    public List<Account> listAccounts() {
        return publicUnitOfWork.readOnly(accountRepository::findAllByDeletedFalse);
    }

    public Account getAccount(Long accountId) {
        return publicUnitOfWork.readOnly(() ->
                accountRepository.findByIdAndDeletedFalse(accountId)
                        .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404))
        );
    }

    public AccountAdminDetailsProjection getAccountAdminDetails(Long accountId) {
        return publicUnitOfWork.readOnly(() -> {
            Account account = accountRepository.findByIdAndDeletedFalse(accountId)
                    .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));

            long totalUsers = controlPlaneUserRepository.countByAccount_IdAndDeletedFalse(accountId);

            ControlPlaneUser admin = controlPlaneUserRepository.findFirstAdminByAccountId(accountId).orElse(null);

            return new AccountAdminDetailsProjection(account, admin, totalUsers);
        });
    }

    // 3) STATUS / SOFT DELETE / RESTORE
    public AccountStatusChangeResult changeAccountStatus(Long accountId, AccountStatusChangeCommand cmd) {
        return accountStatusService.changeAccountStatus(accountId, cmd);
    }

    public void softDeleteAccount(Long accountId) { accountStatusService.softDeleteAccount(accountId); }

    public void restoreAccount(Long accountId) { accountStatusService.restoreAccount(accountId); }

    public List<UserSummaryData> listTenantUsers(Long accountId, boolean onlyOperational) {
        return accountTenantUserService.listTenantUsers(accountId, onlyOperational);
    }

    public void setUserSuspendedByAdmin(Long accountId, Long userId, boolean suspended) {
        accountTenantUserService.setUserSuspendedByAdmin(accountId, userId, suspended);
    }

    // 4) CONSULTAS ADMIN (paginação)
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final long MAX_CREATED_BETWEEN_DAYS = 90;

    private Pageable normalizePageable(Pageable pageable) {
        if (pageable == null) return PageRequest.of(0, DEFAULT_PAGE_SIZE);
        int size = pageable.getPageSize();
        if (size <= 0) size = DEFAULT_PAGE_SIZE;
        if (size > MAX_PAGE_SIZE) size = MAX_PAGE_SIZE;
        return PageRequest.of(pageable.getPageNumber(), size, pageable.getSort());
    }

    private void assertValidCreatedBetweenRange(Instant start, Instant end) {
        if (start == null || end == null) throw new ApiException("INVALID_RANGE", "start/end são obrigatórios", 400);
        if (end.isBefore(start)) throw new ApiException("INVALID_RANGE", "end deve ser >= start", 400);
        Duration d = Duration.between(start, end);
        if (d.toDays() > MAX_CREATED_BETWEEN_DAYS) {
            throw new ApiException("RANGE_TOO_LARGE", "Intervalo máximo é " + MAX_CREATED_BETWEEN_DAYS + " dias", 400);
        }
    }

    public Account findBySlug(String slug) {
        if (slug == null || slug.isBlank()) throw new ApiException("INVALID_SLUG", "slug é obrigatório", 400);
        return publicUnitOfWork.readOnly(() ->
                accountRepository.findBySlugAndDeletedFalseIgnoreCase(slug.trim())
                        .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404))
        );
    }

    public Page<Account> listAccountsByStatus(AccountStatus status, Pageable pageable) {
        if (status == null) throw new ApiException("INVALID_STATUS", "status é obrigatório", 400);
        Pageable p = normalizePageable(pageable);
        return publicUnitOfWork.readOnly(() -> accountRepository.findByStatusAndDeletedFalse(status, p));
    }

    public Page<Account> listAccountsCreatedBetween(Instant start, Instant end, Pageable pageable) {
        assertValidCreatedBetweenRange(start, end);
        Pageable p = normalizePageable(pageable);
        return publicUnitOfWork.readOnly(() -> accountRepository.findAccountsCreatedBetween(start, end, p));
    }

    public Page<Account> searchAccountsByDisplayName(String term, Pageable pageable) {
        if (term == null || term.isBlank()) throw new ApiException("INVALID_SEARCH", "term é obrigatório", 400);
        Pageable p = normalizePageable(pageable);
        return publicUnitOfWork.readOnly(() -> accountRepository.searchByDisplayName(term, p));
    }

    /**
     * Overdue é DATA CIVIL (paymentDueDate é LocalDate/DATE).
     * Mantemos compatibilidade aceitando Instant e convertendo explicitamente para LocalDate em UTC
     * (sem timezone implícito do servidor).
     */
    public List<Account> listOverdueAccounts(Instant today, AccountStatus status) {
        Instant baseInstant = (today != null ? today : appClock.instant());
        LocalDate baseDateUtc = LocalDate.ofInstant(baseInstant, ZoneOffset.UTC);

        AccountStatus st = (status != null ? status : AccountStatus.ACTIVE);

        return publicUnitOfWork.readOnly(() ->
                accountRepository.findOverdueAccountsNotDeleted(st, baseDateUtc)
        );
    }

    public long countOperationalAccounts() {
        return publicUnitOfWork.readOnly(accountRepository::countOperationalAccounts);
    }
}
