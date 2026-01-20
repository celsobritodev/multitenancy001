package brito.com.multitenancy001.controlplane.application;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountAdminDetailsResponse;
import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountResponse;
import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountStatusChangeRequest;
import brito.com.multitenancy001.controlplane.api.dto.accounts.AccountStatusChangeResponse;
import brito.com.multitenancy001.controlplane.api.dto.signup.SignupRequest;
import brito.com.multitenancy001.controlplane.api.dto.users.summary.AccountTenantUserSummaryResponse;
import brito.com.multitenancy001.controlplane.api.mapper.AccountAdminDetailsApiMapper;
import brito.com.multitenancy001.controlplane.api.mapper.AccountApiMapper;
import brito.com.multitenancy001.controlplane.domain.account.Account;
import brito.com.multitenancy001.controlplane.domain.account.AccountStatus;
import brito.com.multitenancy001.controlplane.persistence.account.AccountRepository;
import brito.com.multitenancy001.controlplane.persistence.user.ControlPlaneUserRepository;
import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.shared.executor.PublicExecutor;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountLifecycleService {

    private final ControlPlaneUserRepository controlPlaneUserRepository;
    private final AccountAdminDetailsApiMapper accountAdminDetailsApiMapper;
    private final AccountApiMapper accountApiMapper;
    private final PublicExecutor publicExecutor;
    private final AccountRepository accountRepository;
    private final AccountOnboardingService accountOnboardingService;
    private final AccountStatusService accountStatusService;
    private final AccountTenantUserService accountTenantUserService;

    // =========================================================
    // 1) ONBOARDING / SIGNUP
    // =========================================================

    public AccountResponse createAccount(SignupRequest signupRequest) {
        return accountOnboardingService.createAccount(signupRequest);
    }

    // =========================================================
    // 2) CONSULTAS EXISTENTES
    // =========================================================

    @Transactional(readOnly = true)
    public List<AccountResponse> listAccounts() {
        return publicExecutor.run(() ->
                accountRepository.findAllByDeletedFalse()
                        .stream()
                        .map(accountApiMapper::toResponse)
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(Long accountId) {
        return publicExecutor.run(() -> {
            Account account = accountRepository.findByIdAndDeletedFalse(accountId)
                    .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));
            return accountApiMapper.toResponse(account);
        });
    }

    @Transactional(readOnly = true)
    public AccountAdminDetailsResponse getAccountAdminDetails(Long accountId) {
        return publicExecutor.run(() -> {
            Account account = accountRepository.findByIdAndDeletedFalse(accountId)
                    .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));

            long totalUsers = controlPlaneUserRepository.countByAccountIdAndDeletedFalse(accountId);

            return accountAdminDetailsApiMapper.toResponse(account, null, totalUsers);
        });
    }

    // =========================================================
    // 3) STATUS / SOFT DELETE / RESTORE (EXISTENTES)
    // =========================================================

    public AccountStatusChangeResponse changeAccountStatus(Long accountId, AccountStatusChangeRequest req) {
        return accountStatusService.changeAccountStatus(accountId, req);
    }

    public void softDeleteAccount(Long accountId) {
        accountStatusService.softDeleteAccount(accountId);
    }

    public void restoreAccount(Long accountId) {
        accountStatusService.restoreAccount(accountId);
    }

    public List<AccountTenantUserSummaryResponse> listTenantUsers(Long accountId, boolean onlyActive) {
        return accountTenantUserService.listTenantUsers(accountId, onlyActive);
    }

    public void setUserSuspendedByAdmin(Long accountId, Long userId, boolean suspended) {
        accountTenantUserService.setUserSuspendedByAdmin(accountId, userId, suspended);
    }

    // =========================================================
    // ✅ 4) NOVAS CONSULTAS ADMIN (PAGINAÇÃO + LIMITES)
    // =========================================================

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final long MAX_CREATED_BETWEEN_DAYS = 90;

    private Pageable normalizePageable(Pageable pageable) {
        if (pageable == null) {
            return PageRequest.of(0, DEFAULT_PAGE_SIZE);
        }

        int page = Math.max(0, pageable.getPageNumber());
        int size = pageable.getPageSize();

        if (size <= 0) size = DEFAULT_PAGE_SIZE;
        if (size > MAX_PAGE_SIZE) size = MAX_PAGE_SIZE;

        return PageRequest.of(page, size, pageable.getSort());
    }

    private void assertValidCreatedBetweenRange(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            throw new ApiException("INVALID_DATE_RANGE", "start e end são obrigatórios", 400);
        }
        if (end.isBefore(start)) {
            throw new ApiException("INVALID_DATE_RANGE", "end deve ser >= start", 400);
        }

        long days = Duration.between(start, end).toDays();
        if (days > MAX_CREATED_BETWEEN_DAYS) {
            throw new ApiException(
                    "DATE_RANGE_TOO_LARGE",
                    "Intervalo máximo permitido é de " + MAX_CREATED_BETWEEN_DAYS + " dias",
                    400
            );
        }
    }

    @Transactional(readOnly = true)
    public long countActiveAccounts() {
        return publicExecutor.run(accountRepository::countActiveAccounts);
    }

    @Transactional(readOnly = true)
    public Page<AccountResponse> listAccountsLatest(Pageable pageable) {
        Pageable p = normalizePageable(pageable);

        return publicExecutor.run(() ->
                accountRepository.findByDeletedFalseOrderByCreatedAtDesc(p)
                        .map(accountApiMapper::toResponse)
        );
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccountBySlugIgnoreCase(String slug) {
        if (slug == null || slug.isBlank()) {
            throw new ApiException("INVALID_SLUG", "slug é obrigatório", 400);
        }

        return publicExecutor.run(() -> {
            Account account = accountRepository.findBySlugAndDeletedFalseIgnoreCase(slug.trim())
                    .orElseThrow(() -> new ApiException("ACCOUNT_NOT_FOUND", "Conta não encontrada", 404));
            return accountApiMapper.toResponse(account);
        });
    }

    @Transactional(readOnly = true)
    public Page<AccountResponse> listAccountsByStatus(AccountStatus status, Pageable pageable) {
        if (status == null) {
            throw new ApiException("INVALID_STATUS", "status é obrigatório", 400);
        }

        Pageable p = normalizePageable(pageable);

        return publicExecutor.run(() ->
                accountRepository.findByStatusAndDeletedFalse(status, p)
                        .map(accountApiMapper::toResponse)
        );
    }

    @Transactional(readOnly = true)
    public Page<AccountResponse> listAccountsCreatedBetween(LocalDateTime start, LocalDateTime end, Pageable pageable) {
        assertValidCreatedBetweenRange(start, end);

        Pageable p = normalizePageable(pageable);

        return publicExecutor.run(() ->
                accountRepository.findAccountsCreatedBetween(start, end, p)
                        .map(accountApiMapper::toResponse)
        );
    }

    @Transactional(readOnly = true)
    public Page<AccountResponse> searchAccountsByDisplayName(String term, Pageable pageable) {
        if (term == null || term.isBlank()) {
            throw new ApiException("INVALID_SEARCH", "term é obrigatório", 400);
        }

        Pageable p = normalizePageable(pageable);

        return publicExecutor.run(() ->
                accountRepository.searchByDisplayName(term.trim(), p)
                        .map(accountApiMapper::toResponse)
        );
    }
    
    
 // =========================================================
 // ✅ 5) QUERIES ADMIN (usar métodos do AccountRepository)
 // =========================================================

 @Transactional(readOnly = true)
 public long countAccountsByStatus(AccountStatus status) {
     if (status == null) {
         throw new ApiException("INVALID_STATUS", "status é obrigatório", 400);
     }
     return publicExecutor.run(() -> accountRepository.countByStatusAndDeletedFalse(status));
 }

 @Transactional(readOnly = true)
 public List<AccountResponse> listAccountsByStatuses(List<AccountStatus> statuses) {
     if (statuses == null || statuses.isEmpty()) {
         throw new ApiException("INVALID_STATUS_LIST", "statuses é obrigatório", 400);
     }

     return publicExecutor.run(() ->
             accountRepository.findByStatuses(statuses).stream()
                     .map(accountApiMapper::toResponse)
                     .toList()
     );
 }

 @Transactional(readOnly = true)
 public List<AccountResponse> listExpiredTrials(LocalDateTime date, AccountStatus status) {
     // defaults seguros
     LocalDateTime d = (date != null ? date : LocalDateTime.now());
     AccountStatus st = (status != null ? status : AccountStatus.FREE_TRIAL);

     return publicExecutor.run(() ->
             accountRepository.findExpiredTrials(d, st).stream()
                     .map(accountApiMapper::toResponse)
                     .toList()
     );
 }

 @Transactional(readOnly = true)
 public List<AccountResponse> listOverdueAccounts(LocalDateTime today, AccountStatus status) {
     // defaults seguros
     LocalDateTime t = (today != null ? today : LocalDateTime.now());
     AccountStatus st = (status != null ? status : AccountStatus.ACTIVE);

     return publicExecutor.run(() ->
             accountRepository.findOverdueAccounts(st, t).stream()
                     .map(accountApiMapper::toResponse)
                     .toList()
     );
 }
 
 @Transactional(readOnly = true)
 public long countOperationalAccounts() {
     return publicExecutor.run(accountRepository::countActiveAccounts);
 }

 /** @deprecated use countOperationalAccounts */
 @Deprecated
 @Transactional(readOnly = true)
 public long countActiveAccounts() {
     return countOperationalAccounts();
 }

 

}
