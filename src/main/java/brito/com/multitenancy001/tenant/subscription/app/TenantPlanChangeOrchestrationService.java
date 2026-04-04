package brito.com.multitenancy001.tenant.subscription.app;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.app.subscription.ChangeAccountPlanCommand;
import brito.com.multitenancy001.controlplane.accounts.app.subscription.PlanChangeType;
import brito.com.multitenancy001.controlplane.accounts.app.subscription.PlanEligibilityResult;
import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.SubscriptionPlan;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.domain.billing.BillingCycle;
import brito.com.multitenancy001.shared.domain.billing.PaymentGateway;
import brito.com.multitenancy001.shared.domain.billing.PaymentMethod;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.subscription.api.dto.TenantPlanChangeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fachada fina de orquestração de mudança de plano no contexto Tenant.
 *
 * <p>Objetivo:</p>
 * <ul>
 *   <li>Preservar compatibilidade com os chamadores atuais.</li>
 *   <li>Delegar preview, downgrade e upgrade para serviços especializados.</li>
 *   <li>Manter o fluxo principal de decisão claro e enxuto.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantPlanChangeOrchestrationService {

    private static final String REQUESTED_BY = "tenant_owner";
    private static final String CHANGE_SOURCE = "tenant_self_service";

    private final TenantPlanChangePreviewService tenantPlanChangePreviewService;
    private final TenantPlanDowngradeService tenantPlanDowngradeService;
    private final TenantPlanUpgradeService tenantPlanUpgradeService;
    private final TenantPlanUpgradeSupport tenantPlanUpgradeSupport;

    /**
     * Executa o fluxo completo de mudança de plano no tenant self-service.
     *
     * @param accountId id da conta
     * @param targetPlan plano alvo
     * @param billingCycle ciclo de cobrança do upgrade
     * @param paymentMethod método de pagamento
     * @param paymentGateway gateway de pagamento
     * @param amount valor do upgrade
     * @param planPriceSnapshot snapshot opcional do preço
     * @param currencyCode moeda opcional
     * @param reason motivo opcional
     * @return resposta consolidada
     */
    public TenantPlanChangeResponse execute(
            Long accountId,
            SubscriptionPlan targetPlan,
            BillingCycle billingCycle,
            PaymentMethod paymentMethod,
            PaymentGateway paymentGateway,
            BigDecimal amount,
            BigDecimal planPriceSnapshot,
            String currencyCode,
            String reason
    ) {
        Account account = tenantPlanChangePreviewService.loadAccount(accountId);
        PlanEligibilityResult preview = tenantPlanChangePreviewService.preview(account, targetPlan);

        log.info(
                "Preview calculado para mudança de plano no tenant self-service. accountId={}, currentPlan={}, targetPlan={}, changeType={}, eligible={}",
                accountId,
                preview.currentPlan(),
                preview.targetPlan(),
                preview.changeType(),
                preview.eligible()
        );

        if (preview.changeType() == PlanChangeType.NO_CHANGE) {
            log.warn(
                    "Solicitação rejeitada no tenant: plano alvo igual ao atual. accountId={}, currentPlan={}, targetPlan={}",
                    accountId,
                    preview.currentPlan(),
                    preview.targetPlan()
            );
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "A conta já está no plano informado", 409);
        }

        ChangeAccountPlanCommand command = new ChangeAccountPlanCommand(
                account.getId(),
                targetPlan,
                tenantPlanUpgradeSupport.normalize(reason),
                REQUESTED_BY,
                CHANGE_SOURCE
        );

        if (preview.changeType() == PlanChangeType.DOWNGRADE) {
            return tenantPlanDowngradeService.handleDowngrade(command, preview);
        }

        return tenantPlanUpgradeService.handleUpgrade(
                account,
                preview,
                billingCycle,
                paymentMethod,
                paymentGateway,
                amount,
                planPriceSnapshot,
                currencyCode,
                tenantPlanUpgradeSupport.normalize(reason)
        );
    }
}