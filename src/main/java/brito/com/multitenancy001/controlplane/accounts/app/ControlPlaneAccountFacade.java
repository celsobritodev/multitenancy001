package brito.com.multitenancy001.controlplane.accounts.app;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.app.command.AccountStatusChangeCommand;
import brito.com.multitenancy001.controlplane.accounts.app.dto.AccountAdminDetailsProjection;
import brito.com.multitenancy001.controlplane.accounts.app.dto.AccountStatusChangeResult;
import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.AccountStatus;
import brito.com.multitenancy001.controlplane.signup.app.command.SignupCommand;
import brito.com.multitenancy001.controlplane.signup.app.dto.SignupResult;
import brito.com.multitenancy001.shared.contracts.UserSummaryData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fachada principal do agregado Account no contexto Control Plane.
 *
 * <p><b>Responsabilidades:</b></p>
 * <ul>
 *   <li>Manter uma superfície única de acesso para operações do módulo
 *       Account no Control Plane.</li>
 *   <li>Preservar compatibilidade com controllers e chamadores atuais.</li>
 *   <li>Delegar operações para serviços especializados de command, query,
 *       lookup e administração de usuários tenant.</li>
 * </ul>
 *
 * <p><b>Diretrizes arquiteturais:</b></p>
 * <ul>
 *   <li>Esta classe deve permanecer fina.</li>
 *   <li>Não deve concentrar regra de negócio.</li>
 *   <li>Não deve substituir serviços especializados.</li>
 *   <li>Seu papel é exclusivamente de fachada/orquestração leve.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ControlPlaneAccountFacade {

    private final ControlPlaneAccountCommandService controlPlaneAccountCommandService;
    private final ControlPlaneAccountQueryService controlPlaneAccountQueryService;
    private final ControlPlaneAccountTenantUserAdminService controlPlaneAccountTenantUserAdminService;

    /**
     * Cria uma nova account via fluxo de signup/onboarding.
     *
     * @param signupCommand comando de signup
     * @return resultado do onboarding
     */
    public SignupResult createAccount(SignupCommand signupCommand) {
        log.info("Delegando createAccount para command service.");
        return controlPlaneAccountCommandService.createAccount(signupCommand);
    }

    /**
     * Lista contas não deletadas.
     *
     * @return lista de contas
     */
    public List<Account> listAccounts() {
        return controlPlaneAccountQueryService.listAccounts();
    }

    /**
     * Busca conta não deletada por id.
     *
     * @param accountId id da conta
     * @return conta encontrada
     */
    public Account getAccount(Long accountId) {
        return controlPlaneAccountQueryService.getAccount(accountId);
    }

    /**
     * Retorna visão administrativa detalhada da conta.
     *
     * @param accountId id da conta
     * @return projeção administrativa
     */
    public AccountAdminDetailsProjection getAccountAdminDetails(Long accountId) {
        return controlPlaneAccountQueryService.getAccountAdminDetails(accountId);
    }

    /**
     * Altera status da conta.
     *
     * @param accountId id da conta
     * @param accountStatusChangeCommand comando de mudança de status
     * @return resultado consolidado
     */
    public AccountStatusChangeResult changeAccountStatus(
            Long accountId,
            AccountStatusChangeCommand accountStatusChangeCommand
    ) {
        log.info("Delegando changeAccountStatus para command service. accountId={}", accountId);
        return controlPlaneAccountCommandService.changeAccountStatus(accountId, accountStatusChangeCommand);
    }

    /**
     * Executa soft delete de account.
     *
     * @param accountId id da conta
     */
    public void softDeleteAccount(Long accountId) {
        log.info("Delegando softDeleteAccount para command service. accountId={}", accountId);
        controlPlaneAccountCommandService.softDeleteAccount(accountId);
    }

    /**
     * Restaura account deletada logicamente.
     *
     * @param accountId id da conta
     */
    public void restoreAccount(Long accountId) {
        log.info("Delegando restoreAccount para command service. accountId={}", accountId);
        controlPlaneAccountCommandService.restoreAccount(accountId);
    }

    /**
     * Lista usuários do tenant associado à account.
     *
     * @param accountId id da conta
     * @param onlyOperational indica se retorna apenas usuários operacionais
     * @return lista resumida de usuários
     */
    public List<UserSummaryData> listTenantUsers(Long accountId, boolean onlyOperational) {
        return controlPlaneAccountTenantUserAdminService.listTenantUsers(accountId, onlyOperational);
    }

    /**
     * Suspende ou reativa usuário do tenant por ação administrativa.
     *
     * @param accountId id da conta
     * @param userId id do usuário
     * @param suspended status desejado
     */
    public void setUserSuspendedByAdmin(Long accountId, Long userId, boolean suspended) {
        log.info(
                "Delegando setUserSuspendedByAdmin para tenant user admin service. accountId={}, userId={}",
                accountId,
                userId
        );
        controlPlaneAccountTenantUserAdminService.setUserSuspendedByAdmin(accountId, userId, suspended);
    }

    /**
     * Busca conta por slug.
     *
     * @param slug slug informado
     * @return conta encontrada
     */
    public Account findBySlug(String slug) {
        return controlPlaneAccountQueryService.findBySlug(slug);
    }

    /**
     * Lista contas por status com paginação normalizada.
     *
     * @param status status alvo
     * @param pageable paginação
     * @return página de contas
     */
    public Page<Account> listAccountsByStatus(AccountStatus status, Pageable pageable) {
        return controlPlaneAccountQueryService.listAccountsByStatus(status, pageable);
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
        return controlPlaneAccountQueryService.listAccountsCreatedBetween(start, end, pageable);
    }

    /**
     * Busca contas por displayName.
     *
     * @param term termo de busca
     * @param pageable paginação
     * @return página de contas
     */
    public Page<Account> searchAccountsByDisplayName(String term, Pageable pageable) {
        return controlPlaneAccountQueryService.searchAccountsByDisplayName(term, pageable);
    }

    /**
     * Lista contas overdue com base em data civil UTC.
     *
     * @param today instante base opcional
     * @param status status alvo opcional
     * @return lista de contas overdue
     */
    public List<Account> listOverdueAccounts(Instant today, AccountStatus status) {
        return controlPlaneAccountQueryService.listOverdueAccounts(today, status);
    }

    /**
     * Conta accounts operacionais.
     *
     * @return quantidade de contas operacionais
     */
    public long countOperationalAccounts() {
        return controlPlaneAccountQueryService.countOperationalAccounts();
    }
}