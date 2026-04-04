package brito.com.multitenancy001.controlplane.accounts.app.subscription;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.controlplane.accounts.api.subscription.dto.AccountPlanChangeResponse;
import brito.com.multitenancy001.controlplane.accounts.domain.SubscriptionPlan;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.domain.billing.BillingCycle;
import brito.com.multitenancy001.shared.domain.billing.PaymentGateway;
import brito.com.multitenancy001.shared.domain.billing.PaymentMethod;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Facade de command do Control Plane para mudança de plano por accountId.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Receber a solicitação do controller.</li>
 *   <li>Executar validações leves de entrada.</li>
 *   <li>Delegar integralmente a orchestration para
 *       {@link ControlPlaneAccountPlanChangeCommandService}.</li>
 * </ul>
 *
 * <p>Importante:</p>
 * <ul>
 *   <li>Este service NÃO decide mais o fluxo de downgrade/upgrade.</li>
 *   <li>Este service NÃO faz preview de negócio diretamente.</li>
 *   <li>Este service NÃO acessa repository.</li>
 *   <li>O objetivo é manter a command facade fina e estável.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ControlPlaneAccountSubscriptionCommandFacade {

    private static final String DEFAULT_REQUESTED_BY = "control_plane_admin";

    private final ControlPlaneAccountPlanChangeCommandService orchestrationService;

    /**
     * Solicita mudança efetiva de plano para uma conta.
     *
     * @param accountId id da conta
     * @param targetPlan plano alvo
     * @param billingCycle ciclo de cobrança para upgrade
     * @param paymentMethod método de pagamento para upgrade
     * @param paymentGateway gateway para upgrade
     * @param amount valor para upgrade
     * @param planPriceSnapshot snapshot opcional do preço do plano
     * @param currencyCode moeda opcional
     * @param reason motivo funcional opcional
     * @param requestedBy identificador do solicitante
     * @return resposta consolidada
     */
    public AccountPlanChangeResponse changePlan(
            Long accountId,
            SubscriptionPlan targetPlan,
            BillingCycle billingCycle,
            PaymentMethod paymentMethod,
            PaymentGateway paymentGateway,
            BigDecimal amount,
            BigDecimal planPriceSnapshot,
            String currencyCode,
            String reason,
            String requestedBy
    ) {
        validateBaseInputs(accountId, targetPlan);

        String normalizedRequestedBy = normalizeRequestedBy(requestedBy);

        log.info(
                "Delegando mudança de plano no control plane para orchestration. accountId={}, targetPlan={}, billingCycle={}, paymentMethod={}, paymentGateway={}, amount={}, requestedBy={}",
                accountId,
                targetPlan,
                billingCycle,
                paymentMethod,
                paymentGateway,
                amount,
                normalizedRequestedBy
        );

        return orchestrationService.execute(
                accountId,
                targetPlan,
                billingCycle,
                paymentMethod,
                paymentGateway,
                amount,
                planPriceSnapshot,
                currencyCode,
                reason,
                normalizedRequestedBy
        );
    }

    /**
     * Valida os campos base obrigatórios antes da orchestration.
     *
     * @param accountId id da conta
     * @param targetPlan plano alvo
     */
    private void validateBaseInputs(Long accountId, SubscriptionPlan targetPlan) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        }

        if (targetPlan == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "targetPlan é obrigatório", 400);
        }
    }

    /**
     * Normaliza o identificador do solicitante.
     *
     * @param requestedBy identificador informado
     * @return identificador final
     */
    private String normalizeRequestedBy(String requestedBy) {
        if (!StringUtils.hasText(requestedBy)) {
            return DEFAULT_REQUESTED_BY;
        }
        return requestedBy.trim();
    }
}