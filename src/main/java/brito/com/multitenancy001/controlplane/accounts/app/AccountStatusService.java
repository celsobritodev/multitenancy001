package brito.com.multitenancy001.controlplane.accounts.app;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.app.command.AccountStatusChangeCommand;
import brito.com.multitenancy001.controlplane.accounts.app.dto.AccountStatusChangeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fachada principal de lifecycle e mudança de status de Account.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Manter compatibilidade com chamadores atuais.</li>
 *   <li>Delegar operações de escrita para {@link AccountStatusCommandService}.</li>
 * </ul>
 *
 * <p>Importante:</p>
 * <ul>
 *   <li>Esta classe deve permanecer fina.</li>
 *   <li>Não deve concentrar auditoria, side effects de tenant ou lógica transacional.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountStatusService {

    private final AccountStatusCommandService accountStatusCommandService;

    /**
     * Altera o status da conta e executa side effects necessários.
     *
     * @param accountId id da conta
     * @param accountStatusChangeCommand comando de mudança de status
     * @return resultado consolidado da operação
     */
    public AccountStatusChangeResult changeAccountStatus(
            Long accountId,
            AccountStatusChangeCommand accountStatusChangeCommand
    ) {
        log.info("Delegando changeAccountStatus para command service. accountId={}", accountId);
        return accountStatusCommandService.changeAccountStatus(accountId, accountStatusChangeCommand);
    }

    /**
     * Executa soft delete de conta.
     *
     * @param accountId id da conta
     */
    public void softDeleteAccount(Long accountId) {
        log.info("Delegando softDeleteAccount para command service. accountId={}", accountId);
        accountStatusCommandService.softDeleteAccount(accountId);
    }

    /**
     * Restaura conta deletada logicamente.
     *
     * @param accountId id da conta
     */
    public void restoreAccount(Long accountId) {
        log.info("Delegando restoreAccount para command service. accountId={}", accountId);
        accountStatusCommandService.restoreAccount(accountId);
    }
}