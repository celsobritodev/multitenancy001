package brito.com.multitenancy001.tenant.billing.app;

import java.util.List;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.shared.api.dto.billing.PaymentResponse;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.billing.PaymentQueryService;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.executor.TenantToPublicBridgeExecutor;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Application Service de billing no contexto do Tenant.
 *
 * <p><b>Responsabilidades:</b></p>
 * <ul>
 *   <li>Expor consultas de pagamentos da conta autenticada no contexto tenant.</li>
 *   <li>Delegar consultas ao núcleo público de billing de forma controlada.</li>
 *   <li>Respeitar explicitamente o boundary TENANT -&gt; PUBLIC.</li>
 * </ul>
 *
 * <p><b>Regra arquitetural crítica:</b></p>
 * <ul>
 *   <li>Este service é chamado a partir de fluxos tenant.</li>
 *   <li>Portanto, qualquer acesso ao PUBLIC deve ocorrer via
 *       {@link TenantToPublicBridgeExecutor} antes do
 *       {@link PublicSchemaUnitOfWork}.</li>
 *   <li>Isso evita violação do guard de TenantContext ativo dentro de PUBLIC.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantPaymentService {

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final TenantToPublicBridgeExecutor tenantToPublicBridgeExecutor;
    private final PaymentQueryService paymentQueryService;

    /**
     * Lista os pagamentos da conta informada.
     *
     * <p>Semântica:</p>
     * <ul>
     *   <li>Valida entrada mínima.</li>
     *   <li>Faz bridge explícito TENANT -&gt; PUBLIC.</li>
     *   <li>Executa leitura readOnly no Public Schema.</li>
     * </ul>
     *
     * @param accountId id da conta
     * @return lista de pagamentos da conta
     */
    public List<PaymentResponse> listPaymentsForAccount(Long accountId) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        }

        log.info("Consultando pagamentos da conta no contexto tenant. accountId={}", accountId);

        List<PaymentResponse> result = tenantToPublicBridgeExecutor.call(() ->
                publicSchemaUnitOfWork.readOnly(() -> paymentQueryService.listByAccount(accountId))
        );

        log.info("Consulta de pagamentos concluída com sucesso no contexto tenant. accountId={}, total={}",
                accountId, result != null ? result.size() : 0);

        return result;
    }

    /**
     * Consulta um pagamento específico da conta informada.
     *
     * <p>Semântica:</p>
     * <ul>
     *   <li>Valida accountId e paymentId.</li>
     *   <li>Executa leitura pública com bridge explícito.</li>
     * </ul>
     *
     * @param accountId id da conta
     * @param paymentId id do pagamento
     * @return pagamento encontrado
     */
    public PaymentResponse getPaymentForAccount(Long accountId, Long paymentId) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        }
        if (paymentId == null) {
            throw new ApiException(ApiErrorCode.PAYMENT_ID_REQUIRED, "paymentId é obrigatório", 400);
        }

        log.info("Consultando pagamento específico no contexto tenant. accountId={}, paymentId={}",
                accountId, paymentId);

        PaymentResponse result = tenantToPublicBridgeExecutor.call(() ->
                publicSchemaUnitOfWork.readOnly(() -> paymentQueryService.getByAccount(accountId, paymentId))
        );

        log.info("Consulta de pagamento específico concluída com sucesso no contexto tenant. accountId={}, paymentId={}",
                accountId, paymentId);

        return result;
    }

    /**
     * Verifica se a conta possui pagamento ativo.
     *
     * <p>Semântica:</p>
     * <ul>
     *   <li>Valida accountId.</li>
     *   <li>Executa leitura pública com bridge explícito.</li>
     * </ul>
     *
     * @param accountId id da conta
     * @return {@code true} quando houver pagamento ativo
     */
    public boolean hasActivePayment(Long accountId) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        }

        log.info("Verificando pagamento ativo no contexto tenant. accountId={}", accountId);

        boolean result = tenantToPublicBridgeExecutor.call(() ->
                publicSchemaUnitOfWork.readOnly(() -> paymentQueryService.hasActivePayment(accountId))
        );

        log.info("Verificação de pagamento ativo concluída no contexto tenant. accountId={}, hasActivePayment={}",
                accountId, result);

        return result;
    }
}