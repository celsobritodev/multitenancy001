package brito.com.multitenancy001.tenant.subscription.app;

import java.math.BigDecimal;
import java.time.Instant;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.app.subscription.PlanEligibilityResult;
import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.billing.app.ControlPlanePaymentService;
import brito.com.multitenancy001.shared.api.dto.billing.PaymentRequest;
import brito.com.multitenancy001.shared.api.dto.billing.PaymentResponse;
import brito.com.multitenancy001.shared.domain.billing.BillingCycle;
import brito.com.multitenancy001.shared.domain.billing.PaymentGateway;
import brito.com.multitenancy001.shared.domain.billing.PaymentMethod;
import brito.com.multitenancy001.shared.domain.billing.PaymentPurpose;
import brito.com.multitenancy001.shared.executor.TenantToPublicBridgeExecutor;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.subscription.api.dto.TenantPlanChangeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço responsável pelo fluxo de upgrade no self-service do tenant.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Validar o upgrade com support especializado.</li>
 *   <li>Gerar vigência, cobertura e idempotência.</li>
 *   <li>Executar billing no control plane via crossing explícito.</li>
 *   <li>Montar a resposta consolidada do tenant.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantPlanUpgradeService {

    private final TenantToPublicBridgeExecutor tenantToPublicBridgeExecutor;
    private final ControlPlanePaymentService controlPlanePaymentService;
    private final AppClock appClock;
    private final TenantPlanUpgradeSupport tenantPlanUpgradeSupport;

    /**
     * Processa upgrade via billing do control plane usando bridge explícita.
     *
     * @param account conta alvo
     * @param preview preview calculado
     * @param billingCycle ciclo de cobrança
     * @param paymentMethod método de pagamento
     * @param paymentGateway gateway
     * @param amount valor
     * @param planPriceSnapshot snapshot de preço
     * @param currencyCode moeda
     * @param reason motivo opcional
     * @return resposta consolidada
     */
    public TenantPlanChangeResponse handleUpgrade(
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
        tenantPlanUpgradeSupport.validateUpgradeInputs(
                account,
                preview,
                billingCycle,
                paymentMethod,
                paymentGateway,
                amount
        );

        Instant effectiveFrom = appClock.instant();
        Instant coverageEndDate = tenantPlanUpgradeSupport.resolveCoverageEndDate(effectiveFrom, billingCycle);
        String idempotencyKey = tenantPlanUpgradeSupport.buildUpgradeIdempotencyKey(
                account.getId(),
                account.getSubscriptionPlan(),
                preview.targetPlan(),
                billingCycle,
                amount
        );

        log.info(
                "Iniciando upgrade via billing no tenant self-service. accountId={}, currentPlan={}, targetPlan={}, billingCycle={}, paymentMethod={}, paymentGateway={}, amount={}, effectiveFrom={}, coverageEndDate={}, idempotencyKey={}",
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

        PaymentResponse payment = tenantToPublicBridgeExecutor.call(() ->
                controlPlanePaymentService.processPaymentForMyAccount(
                        new PaymentRequest(
                                amount,
                                paymentMethod,
                                paymentGateway,
                                tenantPlanUpgradeSupport.buildDescription(account, preview.targetPlan(), reason),
                                preview.targetPlan(),
                                billingCycle,
                                PaymentPurpose.PLAN_UPGRADE,
                                planPriceSnapshot,
                                tenantPlanUpgradeSupport.normalizeCurrency(currencyCode),
                                effectiveFrom,
                                coverageEndDate,
                                idempotencyKey
                        )
                )
        );

        log.info(
                "Upgrade concluído via billing no tenant. accountId={}, paymentId={}, paymentStatus={}, oldPlan={}, targetPlan={}, idempotencyKey={}",
                account.getId(),
                payment.id(),
                payment.paymentStatus(),
                account.getSubscriptionPlan(),
                preview.targetPlan(),
                idempotencyKey
        );

        return new TenantPlanChangeResponse(
                account.getId(),
                account.getSubscriptionPlan().name(),
                payment.targetPlan() != null ? payment.targetPlan().name() : account.getSubscriptionPlan().name(),
                preview.targetPlan().name(),
                preview.changeType().name(),
                true,
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
                "Upgrade processado com sucesso."
        );
    }
}