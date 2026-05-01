package brito.com.multitenancy001.controlplane.accounts.app.subscription;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.api.subscription.dto.AccountPlanChangeResponse;
import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.SubscriptionPlan;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.domain.billing.BillingCycle;
import brito.com.multitenancy001.shared.domain.billing.PaymentGateway;
import brito.com.multitenancy001.shared.domain.billing.PaymentMethod;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fachada fina de orquestração de mudança de plano no contexto do Control Plane.
 *
 * <p>Objetivo:</p>
 * <ul>
 *   <li>Preservar compatibilidade com os chamadores atuais.</li>
 *   <li>Delegar preview, downgrade e upgrade para serviços especializados.</li>
 *   <li>Manter o fluxo principal de decisão claro e enxuto.</li>
 * </ul>
 *
 * <p><b>Regra V33:</b></p>
 * <ul>
 *   <li>Sem ApiException com status hardcoded.</li>
 *   <li>Sem validação inline.</li>
 *   <li>Regra de negócio intacta.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ControlPlaneAccountPlanChangeCommandService {

    private static final String CHANGE_SOURCE = "control_plane_admin";

    private final ControlPlaneAccountPlanChangePreviewService controlPlaneAccountPlanChangePreviewService;
    private final ControlPlaneAccountPlanDowngradeService controlPlaneAccountPlanDowngradeService;
    private final ControlPlaneAccountPlanUpgradeService controlPlaneAccountPlanUpgradeService;
    private final ControlPlaneAccountPlanUpgradeNormalizer controlPlaneAccountPlanUpgradeNormalizer;

    public AccountPlanChangeResponse execute(
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

        Account account = controlPlaneAccountPlanChangePreviewService.loadAccount(accountId);
        PlanEligibilityResult preview = controlPlaneAccountPlanChangePreviewService.preview(account, targetPlan);

        String normalizedRequestedBy = controlPlaneAccountPlanUpgradeNormalizer.normalize(requestedBy);

        log.info(
                "Preview calculado para mudança de plano no control plane. accountId={}, currentPlan={}, targetPlan={}, changeType={}, eligible={}, requestedBy={}",
                accountId,
                preview.currentPlan(),
                preview.targetPlan(),
                preview.changeType(),
                preview.eligible(),
                normalizedRequestedBy
        );

        if (preview.changeType() == PlanChangeType.NO_CHANGE) {

            log.warn(
                    "Solicitação rejeitada: plano alvo igual ao atual. accountId={}, currentPlan={}, targetPlan={}, requestedBy={}",
                    accountId,
                    preview.currentPlan(),
                    preview.targetPlan(),
                    normalizedRequestedBy
            );

            throw new ApiException(
                    ApiErrorCode.INVALID_REQUEST,
                    "A conta já está no plano informado"
            );
        }

        ChangeAccountPlanCommand command = new ChangeAccountPlanCommand(
                accountId,
                targetPlan,
                controlPlaneAccountPlanUpgradeNormalizer.normalize(reason),
                normalizedRequestedBy,
                CHANGE_SOURCE
        );

        if (preview.changeType() == PlanChangeType.DOWNGRADE) {
            return controlPlaneAccountPlanDowngradeService.handleDowngrade(command, preview);
        }

        return controlPlaneAccountPlanUpgradeService.handleUpgrade(
                account,
                preview,
                billingCycle,
                paymentMethod,
                paymentGateway,
                amount,
                planPriceSnapshot,
                currencyCode,
                controlPlaneAccountPlanUpgradeNormalizer.normalize(reason)
        );
    }
}