package brito.com.multitenancy001.controlplane.accounts.app;

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
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.contracts.UserSummaryData;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Application Service do Control Plane para Accounts.
 *
 * Responsabilidades:
 * - Orquestrar casos de uso (signup/consultas/admin).
 * - Aplicar constraints de consulta (paginação/range) sem vazar detalhes para controllers.
 * - Delegar mudanças de estado e side-effects (tenant users) para serviços especializados.
 *
 * Regras:
 * - Controllers não acessam repositórios diretamente.
 * - Leituras devem preferir readOnly() e comandos devem preferir tx().
 * - Status HTTP e mensagens default derivam de ApiErrorCode (evita duplicação semântica).
 */
@Service
@RequiredArgsConstructor
public class AccountAppService {

    private final ControlPlaneUserRepository controlPlaneUserRepository;
    private final AccountRepository accountRepository;
    private final AccountOnboardingService accountOnboardingService;
    private final AccountStatusService accountStatusService;
    private final AccountTenantUserService accountTenantUserService;
    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final AppClock appClock;

    // =========================================================
    // 1) ONBOARDING / SIGNUP
    // =========================================================

    public SignupResult createAccount(SignupCommand signupCommand) {
        /* Orquestra fluxo de onboarding e criação de Account (delegado ao onboarding). */
        if (signupCommand == null) throw new ApiException(ApiErrorCode.INVALID_REQUEST, "signupCommand é obrigatório", 400);
        return accountOnboardingService.createAccount(signupCommand);
    }

    // =========================================================
    // 2) CONSULTAS
    // =========================================================

    public List<Account> listAccounts() {
        /* Lista contas não deletadas. */
        return publicSchemaUnitOfWork.readOnly(accountRepository::findAllByDeletedFalse);
    }

    public Account getAccount(Long accountId) {
        /* Busca conta por id (não deletada). */
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);

        return publicSchemaUnitOfWork.readOnly(() ->
                accountRepository.findByIdAndDeletedFalse(accountId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.ACCOUNT_NOT_FOUND))
        );
    }

    public AccountAdminDetailsProjection getAccountAdminDetails(Long accountId) {
        /* Retorna visão admin com total de usuários e primeiro admin (se existir). */
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);

        return publicSchemaUnitOfWork.readOnly(() -> {
            Account account = accountRepository.findByIdAndDeletedFalse(accountId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.ACCOUNT_NOT_FOUND));

            long totalUsers = controlPlaneUserRepository.countByAccount_IdAndDeletedFalse(accountId);
            ControlPlaneUser admin = controlPlaneUserRepository.findFirstAdminByAccountId(accountId).orElse(null);

            return new AccountAdminDetailsProjection(account, admin, totalUsers);
        });
    }

    // =========================================================
    // 3) STATUS / SOFT DELETE / RESTORE
    // =========================================================

    public AccountStatusChangeResult changeAccountStatus(Long accountId, AccountStatusChangeCommand cmd) {
        /* Orquestra mudança de status (delegado ao service específico). */
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);
        if (cmd == null) throw new ApiException(ApiErrorCode.INVALID_REQUEST, "cmd é obrigatório", 400);

        return accountStatusService.changeAccountStatus(accountId, cmd);
    }

    public void softDeleteAccount(Long accountId) {
        /* Soft delete de account (delegado ao service específico). */
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);
        accountStatusService.softDeleteAccount(accountId);
    }

    public void restoreAccount(Long accountId) {
        /* Restaura account deletada (delegado ao service específico). */
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);
        accountStatusService.restoreAccount(accountId);
    }

    public List<UserSummaryData> listTenantUsers(Long accountId, boolean onlyOperational) {
        /* Lista usuários do tenant associado à account. */
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);
        return accountTenantUserService.listTenantUsers(accountId, onlyOperational);
    }

    public void setUserSuspendedByAdmin(Long accountId, Long userId, boolean suspended) {
        /* Suspende/reativa usuário do tenant via ação admin. */
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);
        if (userId == null) throw new ApiException(ApiErrorCode.USER_ID_REQUIRED, "userId é obrigatório", 400);

        accountTenantUserService.setUserSuspendedByAdmin(accountId, userId, suspended);
    }

    // =========================================================
    // 4) CONSULTAS ADMIN (paginação)
    // =========================================================

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final long MAX_CREATED_BETWEEN_DAYS = 90;

    private Pageable normalizePageable(Pageable pageable) {
        /* Normaliza limites de paginação para evitar abuso e manter comportamento consistente. */
        if (pageable == null) return PageRequest.of(0, DEFAULT_PAGE_SIZE);

        int page = Math.max(0, pageable.getPageNumber());
        int size = pageable.getPageSize();

        if (size <= 0) size = DEFAULT_PAGE_SIZE;
        if (size > MAX_PAGE_SIZE) size = MAX_PAGE_SIZE;

        return PageRequest.of(page, size, pageable.getSort());
    }

    private void assertValidCreatedBetweenRange(Instant start, Instant end) {
        /* Valida intervalo (start/end) para consultas por data de criação. */
        if (start == null || end == null) throw new ApiException(ApiErrorCode.INVALID_RANGE, "start/end são obrigatórios", 400);
        if (end.isBefore(start)) throw new ApiException(ApiErrorCode.INVALID_RANGE, "end deve ser >= start", 400);

        Duration d = Duration.between(start, end);
        if (d.toDays() > MAX_CREATED_BETWEEN_DAYS) {
            throw new ApiException(ApiErrorCode.RANGE_TOO_LARGE, "Intervalo máximo é " + MAX_CREATED_BETWEEN_DAYS + " dias", 400);
        }
    }

    public Account findBySlug(String slug) {
        /* Busca conta por slug (case-insensitive) e não deletada. */
        if (slug == null || slug.isBlank()) throw new ApiException(ApiErrorCode.INVALID_SLUG, "slug é obrigatório", 400);

        String normalized = slug.trim();

        return publicSchemaUnitOfWork.readOnly(() ->
                accountRepository.findBySlugAndDeletedFalseIgnoreCase(normalized)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.ACCOUNT_NOT_FOUND))
        );
    }

    public Page<Account> listAccountsByStatus(AccountStatus status, Pageable pageable) {
        /* Lista contas por status com paginação normalizada. */
        if (status == null) throw new ApiException(ApiErrorCode.STATUS_REQUIRED, "status é obrigatório", 400);

        Pageable p = normalizePageable(pageable);
        return publicSchemaUnitOfWork.readOnly(() -> accountRepository.findByStatusAndDeletedFalse(status, p));
    }

    public Page<Account> listAccountsCreatedBetween(Instant start, Instant end, Pageable pageable) {
        /* Lista contas criadas em intervalo, com validação e paginação normalizada. */
        assertValidCreatedBetweenRange(start, end);

        Pageable p = normalizePageable(pageable);
        return publicSchemaUnitOfWork.readOnly(() -> accountRepository.findAccountsCreatedBetween(start, end, p));
    }

    public Page<Account> searchAccountsByDisplayName(String term, Pageable pageable) {
        /* Busca por displayName com paginação normalizada. */
        if (term == null || term.isBlank()) throw new ApiException(ApiErrorCode.INVALID_SEARCH, "term é obrigatório", 400);

        String normalized = term.trim();
        Pageable p = normalizePageable(pageable);

        return publicSchemaUnitOfWork.readOnly(() -> accountRepository.searchByDisplayName(normalized, p));
    }

    /**
     * Overdue é DATA CIVIL (paymentDueDate é LocalDate/DATE).
     * Mantemos compatibilidade aceitando Instant e convertendo explicitamente para LocalDate em UTC
     * (sem timezone implícito do servidor).
     */
    public List<Account> listOverdueAccounts(Instant today, AccountStatus status) {
        /* Lista contas overdue com base em data civil UTC (compatibilidade). */
        Instant baseInstant = (today != null ? today : appClock.instant());
        LocalDate baseDateUtc = LocalDate.ofInstant(baseInstant, ZoneOffset.UTC);

        AccountStatus st = (status != null ? status : AccountStatus.ACTIVE);

        return publicSchemaUnitOfWork.readOnly(() ->
                accountRepository.findOverdueAccountsNotDeleted(st, baseDateUtc)
        );
    }

    public long countOperationalAccounts() {
        /* Conta accounts operacionais. */
        return publicSchemaUnitOfWork.readOnly(accountRepository::countOperationalAccounts);
    }
}