package brito.com.multitenancy001.controlplane.accounts.app.subscription;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.SubscriptionPlan;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.domain.billing.BillingCycle;
import brito.com.multitenancy001.shared.domain.billing.PaymentGateway;
import brito.com.multitenancy001.shared.domain.billing.PaymentMethod;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.extern.slf4j.Slf4j;

/**
 * Helper compartilhado do fluxo de upgrade no contexto do Control Plane.
 *
 * <p><b>Regra V33:</b></p>
 * <ul>
 *   <li>Sem status HTTP hardcoded.</li>
 *   <li>Validação encapsulada em métodos privados.</li>
 *   <li>Sem duplicação de lógica.</li>
 * </ul>
 */
@Component
@Slf4j
public class ControlPlaneAccountPlanUpgradeNormalizer {

    private static final String DEFAULT_CURRENCY = "BRL";

    public void validateUpgradeInputs(
            Account account,
            PlanEligibilityResult preview,
            BillingCycle billingCycle,
            PaymentMethod paymentMethod,
            PaymentGateway paymentGateway,
            BigDecimal amount
    ) {

        validateBillingCycle(account, preview, billingCycle);
        validatePaymentMethod(account, preview, billingCycle, paymentMethod);
        validatePaymentGateway(account, preview, billingCycle, paymentGateway);
        validateAmount(account, preview, billingCycle, amount);
    }

    private void validateBillingCycle(
            Account account,
            PlanEligibilityResult preview,
            BillingCycle billingCycle
    ) {
        if (billingCycle == null) {
            log.warn(
                    "Upgrade rejeitado: billingCycle ausente. accountId={}, targetPlan={}",
                    account.getId(),
                    preview.targetPlan()
            );
            throw new ApiException(
                    ApiErrorCode.INVALID_REQUEST,
                    "billingCycle é obrigatório para upgrade"
            );
        }

        if (billingCycle == BillingCycle.ONE_TIME) {
            log.warn(
                    "Upgrade rejeitado: billingCycle ONE_TIME inválido. accountId={}, currentPlan={}, targetPlan={}",
                    account.getId(),
                    account.getSubscriptionPlan(),
                    preview.targetPlan()
            );
            throw new ApiException(
                    ApiErrorCode.INVALID_REQUEST,
                    "billingCycle ONE_TIME não é permitido para upgrade de plano"
            );
        }
    }

    private void validatePaymentMethod(
            Account account,
            PlanEligibilityResult preview,
            BillingCycle billingCycle,
            PaymentMethod paymentMethod
    ) {
        if (paymentMethod == null) {
            log.warn(
                    "Upgrade rejeitado: paymentMethod ausente. accountId={}, targetPlan={}, billingCycle={}",
                    account.getId(),
                    preview.targetPlan(),
                    billingCycle
            );
            throw new ApiException(
                    ApiErrorCode.INVALID_REQUEST,
                    "paymentMethod é obrigatório para upgrade"
            );
        }
    }

    private void validatePaymentGateway(
            Account account,
            PlanEligibilityResult preview,
            BillingCycle billingCycle,
            PaymentGateway paymentGateway
    ) {
        if (paymentGateway == null) {
            log.warn(
                    "Upgrade rejeitado: paymentGateway ausente. accountId={}, targetPlan={}, billingCycle={}",
                    account.getId(),
                    preview.targetPlan(),
                    billingCycle
            );
            throw new ApiException(
                    ApiErrorCode.INVALID_REQUEST,
                    "paymentGateway é obrigatório para upgrade"
            );
        }
    }

    private void validateAmount(
            Account account,
            PlanEligibilityResult preview,
            BillingCycle billingCycle,
            BigDecimal amount
    ) {
        if (amount == null || amount.signum() <= 0) {
            log.warn(
                    "Upgrade rejeitado: amount inválido. accountId={}, targetPlan={}, billingCycle={}, amount={}",
                    account.getId(),
                    preview.targetPlan(),
                    billingCycle,
                    amount
            );
            throw new ApiException(
                    ApiErrorCode.INVALID_REQUEST,
                    "amount deve ser maior que zero para upgrade"
            );
        }
    }

    public Instant resolveCoverageEndDate(Instant effectiveFrom, BillingCycle billingCycle) {
        ZonedDateTime base = ZonedDateTime.ofInstant(effectiveFrom, ZoneOffset.UTC);

        return switch (billingCycle) {
            case MONTHLY -> base.plusMonths(1).toInstant();
            case YEARLY -> base.plusYears(1).toInstant();
            case ONE_TIME -> throw new ApiException(
                    ApiErrorCode.INVALID_REQUEST,
                    "billingCycle ONE_TIME não possui cobertura recorrente"
            );
        };
    }

    public String buildUpgradeDescription(Account account, SubscriptionPlan targetPlan, String reason) {
        StringBuilder description = new StringBuilder()
                .append("Upgrade de plano da conta ")
                .append(account.getId())
                .append(" de ")
                .append(account.getSubscriptionPlan())
                .append(" para ")
                .append(targetPlan);

        if (StringUtils.hasText(reason)) {
            description.append(". Motivo: ").append(reason.trim());
        }

        return description.toString();
    }

    public String buildUpgradeIdempotencyKey(
            Long accountId,
            SubscriptionPlan currentPlan,
            SubscriptionPlan targetPlan,
            BillingCycle billingCycle,
            BigDecimal amount
    ) {
        return String.format(
                "CP-UPGRADE:%s:%s:%s:%s:%s",
                accountId,
                currentPlan != null ? currentPlan.name() : "NULL",
                targetPlan != null ? targetPlan.name() : "NULL",
                billingCycle != null ? billingCycle.name() : "NULL",
                amount != null ? amount.stripTrailingZeros().toPlainString() : "NULL"
        );
    }

    public String normalizeCurrency(String currencyCode) {
        if (!StringUtils.hasText(currencyCode)) {
            return DEFAULT_CURRENCY;
        }
        return currencyCode.trim().toUpperCase();
    }

    public String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}