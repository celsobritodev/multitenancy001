package brito.com.multitenancy001.controlplane.accounts.app.subscription;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.api.subscription.dto.AccountPlanChangeResponse;
import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.billing.app.ControlPlanePaymentFacade;
import brito.com.multitenancy001.shared.api.dto.billing.AdminPaymentRequest;
import brito.com.multitenancy001.shared.api.dto.billing.PaymentResponse;
import brito.com.multitenancy001.shared.domain.billing.BillingCycle;
import brito.com.multitenancy001.shared.domain.billing.PaymentGateway;
import brito.com.multitenancy001.shared.domain.billing.PaymentMethod;
import brito.com.multitenancy001.shared.domain.billing.PaymentPurpose;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço responsável pelo fluxo de upgrade no contexto do Control Plane.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Validar o upgrade com support especializado.</li>
 *   <li>Gerar vigência, cobertura e idempotência.</li>
 *   <li>Executar billing binding administrativo.</li>
 *   <li>Montar a resposta consolidada.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ControlPlaneAccountPlanUpgradeService {

    private final ControlPlanePaymentFacade controlPlanePaymentService;
    private final AppClock appClock;
    private final ControlPlaneAccountPlanUpgradeNormalizer controlPlaneAccountPlanUpgradeNormalizer;

    /**
     * Processa upgrade via billing binding.
     *
     * @param account conta alvo
     * @param preview preview calculado
     * @param billingCycle ciclo de cobrança
     * @param paymentMethod método de pagamento
     * @param paymentGateway gateway
     * @param amount valor a cobrar
     * @param planPriceSnapshot snapshot opcional do preço
     * @param currencyCode moeda opcional
     * @param reason motivo funcional
     * @return resposta final
     */
    public AccountPlanChangeResponse handleUpgrade(
            Account account,
            PlanEligibilityResult preview,
            BillingCycle billingCycle,
            PaymentMethod paymentMethod,
            PaymentGateway paymentGateway,
            BigDecimal amount,
            BigDecimal planPriceSnapshot,
            String currencyCode,
            String reason
    ) {
        controlPlaneAccountPlanUpgradeNormalizer.validateUpgradeInputs(
                account,
                preview,
                billingCycle,
                paymentMethod,
                paymentGateway,
                amount
        );

        Instant effectiveFrom = appClock.instant();
        Instant coverageEndDate = controlPlaneAccountPlanUpgradeNormalizer.resolveCoverageEndDate(
                effectiveFrom,
                billingCycle
        );
        String idempotencyKey = controlPlaneAccountPlanUpgradeNormalizer.buildUpgradeIdempotencyKey(
                account.getId(),
                account.getSubscriptionPlan(),
                preview.targetPlan(),
                billingCycle,
                amount
        );

        log.info(
                "Iniciando upgrade via billing no control plane. accountId={}, currentPlan={}, targetPlan={}, billingCycle={}, paymentMethod={}, paymentGateway={}, amount={}, effectiveFrom={}, coverageEndDate={}, idempotencyKey={}",
                account.getId(),
                account.getSubscriptionPlan(),
                preview.targetPlan(),
                billingCycle,
                paymentMethod,
                paymentGateway,
                amount,
                effectiveFrom,
                coverageEndDate,
                idempotencyKey
        );

        PaymentResponse payment = controlPlanePaymentService.processPaymentForAccount(
                new AdminPaymentRequest(
                        account.getId(),
                        amount,
                        paymentMethod,
                        paymentGateway,
                        controlPlaneAccountPlanUpgradeNormalizer.buildUpgradeDescription(
                                account,
                                preview.targetPlan(),
                                reason
                        ),
                        preview.targetPlan(),
                        billingCycle,
                        PaymentPurpose.PLAN_UPGRADE,
                        planPriceSnapshot,
                        controlPlaneAccountPlanUpgradeNormalizer.normalizeCurrency(currencyCode),
                        effectiveFrom,
                        coverageEndDate,
                        idempotencyKey
                )
        );

        log.info(
                "Upgrade via billing concluído no control plane. accountId={}, paymentId={}, paymentStatus={}, oldPlan={}, targetPlan={}, idempotencyKey={}",
                account.getId(),
                payment.id(),
                payment.paymentStatus(),
                account.getSubscriptionPlan(),
                preview.targetPlan(),
                idempotencyKey
        );

        return new AccountPlanChangeResponse(
                account.getId(),
                account.getSubscriptionPlan().name(),
                payment.targetPlan() != null ? payment.targetPlan().name() : account.getSubscriptionPlan().name(),
                preview.targetPlan().name(),
                preview.changeType().name(),
                preview.eligible(),
                true,
                payment.id(),
                payment.paymentStatus() != null ? payment.paymentStatus().name() : null,
                payment.paymentMethod() != null ? payment.paymentMethod().name() : null,
                payment.paymentGateway() != null ? payment.paymentGateway().name() : null,
                payment.billingCycle() != null ? payment.billingCycle().name() : null,
                payment.amount(),
                payment.currencyCode(),
                payment.effectiveFrom(),
                payment.coverageEndDate(),
                "Upgrade processado via billing com sucesso."
        );
    }
}