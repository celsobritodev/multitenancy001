package brito.com.multitenancy001.controlplane.accounts.app.query;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.AccountStatus;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;

/**
 * Serviço de lookup do agregado {@link Account} no contexto Control Plane.
 *
 * <p><b>Responsabilidades:</b></p>
 * <ul>
 *   <li>Executar consultas pontuais e determinísticas de Account.</li>
 *   <li>Realizar buscas diretas por identificador com diferentes critérios (enabled / qualquer estado).</li>
 *   <li>Executar consultas simples baseadas em critérios (status e vencimento).</li>
 *   <li>Encapsular acesso ao {@link AccountRepository} sob {@link PublicSchemaUnitOfWork}.</li>
 * </ul>
 *
 * <p><b>Diretrizes arquiteturais:</b></p>
 * <ul>
 *   <li>Não implementa regra de negócio complexa.</li>
 *   <li>Não realiza orquestração de fluxos.</li>
 *   <li>Não acessa outros agregados.</li>
 *   <li>Executa apenas operações de leitura.</li>
 *   <li>Garante execução no schema público via {@link PublicSchemaUnitOfWork}.</li>
 * </ul>
 *
 * <p><b>Escopo:</b></p>
 * <ul>
 *   <li>Este serviço não substitui o {@code ControlPlaneAccountQueryService} principal.</li>
 *   <li>Seu foco é fornecer operações de lookup e consultas auxiliares reutilizáveis.</li>
 * </ul>
 *
 * <p><b>Observação:</b></p>
 * <ul>
 *   <li>Validações básicas de entrada são realizadas no boundary do método.</li>
 *   <li>Erros são padronizados via {@link ApiException}.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ControlPlaneAccountLookupService {

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final AccountRepository accountRepository;

    /**
     * Busca uma conta habilitada (operacional) pelo id.
     *
     * @param id identificador da conta
     * @return conta habilitada encontrada
     * @throws ApiException se o id for nulo ou a conta não estiver habilitada
     */
    public Account getEnabledById(Long id) {
        return publicSchemaUnitOfWork.readOnly(() -> {
            if (id == null) throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "id é obrigatório", 400);

            return accountRepository.findEnabledById(id)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.ACCOUNT_NOT_ENABLED, "Conta não encontrada ou não operacional", 404));
        });
    }

    /**
     * Busca qualquer conta pelo id, independentemente do estado.
     *
     * @param id identificador da conta
     * @return conta encontrada
     * @throws ApiException se o id for nulo ou a conta não existir
     */
    public Account getAnyById(Long id) {
        return publicSchemaUnitOfWork.readOnly(() -> {
            if (id == null) throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "id é obrigatório", 400);

            return accountRepository.findAnyById(id)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.ACCOUNT_NOT_FOUND, "Conta não encontrada", 404));
        });
    }

    /**
     * Conta o número de contas não deletadas para os status informados.
     *
     * @param statuses lista de status alvo
     * @return quantidade de contas que correspondem aos status informados
     * @throws ApiException se a lista de status for nula ou vazia
     */
    public long countByStatusesNotDeleted(List<AccountStatus> statuses) {
        return publicSchemaUnitOfWork.readOnly(() -> {
            if (statuses == null || statuses.isEmpty()) {
                throw new ApiException(ApiErrorCode.ACCOUNT_STATUSES_REQUIRED, "statuses é obrigatório", 400);
            }
            return accountRepository.countByStatusesAndDeletedFalse(statuses);
        });
    }

    /**
     * Lista contas não deletadas com data de vencimento anterior à data informada.
     *
     * @param date data limite para comparação
     * @return lista de contas com vencimento anterior à data
     * @throws ApiException se a data for nula
     */
    public List<Account> findPaymentDueBeforeNotDeleted(LocalDate date) {
        return publicSchemaUnitOfWork.readOnly(() -> {
            if (date == null) throw new ApiException(ApiErrorCode.DATE_REQUIRED, "date é obrigatório", 400);

            return accountRepository.findByPaymentDueDateBeforeAndDeletedFalse(date);
        });
    }
}