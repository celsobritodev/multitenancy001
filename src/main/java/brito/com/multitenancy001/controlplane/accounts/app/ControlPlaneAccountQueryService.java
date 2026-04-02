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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de consulta do agregado Account no Control Plane.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Executar leituras simples e administrativas de Account.</li>
 *   <li>Aplicar validações de filtros e paginação.</li>
 *   <li>Centralizar consultas paginadas e buscas auxiliares do módulo.</li>
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
    private final ControlPlaneAccountAdminQuerySupport controlPlaneAccountAdminQuerySupport;

    /**
     * Lista contas não deletadas.
     *
     * @return lista de contas
     */
    public List<Account> listAccounts() {
        log.debug("Listando contas não deletadas.");
        return publicSchemaUnitOfWork.readOnly(accountRepository::findAllByDeletedFalse);
    }

    /**
     * Busca conta não deletada por id.
     *
     * @param accountId id da conta
     * @return conta encontrada
     */
    public Account getAccount(Long accountId) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED);
        }

        return publicSchemaUnitOfWork.readOnly(() ->
                accountRepository.findByIdAndDeletedFalse(accountId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.ACCOUNT_NOT_FOUND))
        );
    }

    /**
     * Retorna visão administrativa detalhada da conta.
     *
     * @param accountId id da conta
     * @return projeção administrativa
     */
    public AccountAdminDetailsProjection getAccountAdminDetails(Long accountId) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED);
        }

        return publicSchemaUnitOfWork.readOnly(() -> {
            Account account = accountRepository.findByIdAndDeletedFalse(accountId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.ACCOUNT_NOT_FOUND));

            long totalUsers = controlPlaneUserRepository.countByAccount_IdAndDeletedFalse(accountId);
            ControlPlaneUser admin = controlPlaneUserRepository.findFirstAdminByAccountId(accountId).orElse(null);

            return new AccountAdminDetailsProjection(account, admin, totalUsers);
        });
    }

    /**
     * Busca conta por slug não deletada.
     *
     * @param slug slug informado
     * @return conta encontrada
     */
    public Account findBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_SLUG, "slug é obrigatório", 400);
        }

        String normalized = slug.trim();

        return publicSchemaUnitOfWork.readOnly(() ->
                accountRepository.findBySlugAndDeletedFalseIgnoreCase(normalized)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.ACCOUNT_NOT_FOUND))
        );
    }

    /**
     * Lista contas por status com paginação normalizada.
     *
     * @param status status alvo
     * @param pageable paginação
     * @return página de contas
     */
    public Page<Account> listAccountsByStatus(AccountStatus status, Pageable pageable) {
        if (status == null) {
            throw new ApiException(ApiErrorCode.STATUS_REQUIRED, "status é obrigatório", 400);
        }

        Pageable normalizedPageable = controlPlaneAccountAdminQuerySupport.normalizePageable(pageable);

        return publicSchemaUnitOfWork.readOnly(() ->
                accountRepository.findByStatusAndDeletedFalse(status, normalizedPageable)
        );
    }

    /**
     * Lista contas criadas em intervalo informado.
     *
     * @param start início do intervalo
     * @param end fim do intervalo
     * @param pageable paginação
     * @return página de contas
     */
    public Page<Account> listAccountsCreatedBetween(Instant start, Instant end, Pageable pageable) {
        controlPlaneAccountAdminQuerySupport.assertValidCreatedBetweenRange(start, end);

        Pageable normalizedPageable = controlPlaneAccountAdminQuerySupport.normalizePageable(pageable);

        return publicSchemaUnitOfWork.readOnly(() ->
                accountRepository.findAccountsCreatedBetween(start, end, normalizedPageable)
        );
    }

    /**
     * Busca contas por displayName com paginação normalizada.
     *
     * @param term termo de busca
     * @param pageable paginação
     * @return página de contas
     */
    public Page<Account> searchAccountsByDisplayName(String term, Pageable pageable) {
        if (term == null || term.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_SEARCH, "term é obrigatório", 400);
        }

        String normalized = term.trim();
        Pageable normalizedPageable = controlPlaneAccountAdminQuerySupport.normalizePageable(pageable);

        return publicSchemaUnitOfWork.readOnly(() ->
                accountRepository.searchByDisplayName(normalized, normalizedPageable)
        );
    }

    /**
     * Lista contas overdue com base em data civil UTC.
     *
     * @param today instante base opcional
     * @param status status alvo opcional
     * @return lista de contas overdue
     */
    public List<Account> listOverdueAccounts(Instant today, AccountStatus status) {
        Instant baseInstant = (today != null ? today : appClock.instant());
        LocalDate baseDateUtc = LocalDate.ofInstant(baseInstant, ZoneOffset.UTC);

        AccountStatus effectiveStatus = (status != null ? status : AccountStatus.ACTIVE);

        return publicSchemaUnitOfWork.readOnly(() ->
                accountRepository.findOverdueAccountsNotDeleted(effectiveStatus, baseDateUtc)
        );
    }

    /**
     * Conta accounts operacionais.
     *
     * @return quantidade de contas operacionais
     */
    public long countOperationalAccounts() {
        return publicSchemaUnitOfWork.readOnly(accountRepository::countOperationalAccounts);
    }
}