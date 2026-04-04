package brito.com.multitenancy001.tenant.subscription.app;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.domain.SubscriptionPlan;
import brito.com.multitenancy001.integration.security.TenantRequestIdentityService;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.domain.billing.BillingCycle;
import brito.com.multitenancy001.shared.domain.billing.PaymentGateway;
import brito.com.multitenancy001.shared.domain.billing.PaymentMethod;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.tenant.subscription.api.dto.TenantPlanChangeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantSubscriptionCommandService {

    private final TenantRequestIdentityService requestIdentity;
    private final TenantPlanChangeCommandService orchestrationService;

    public TenantPlanChangeResponse changePlan(
            SubscriptionPlan targetPlan,
            BillingCycle billingCycle,
            PaymentMethod paymentMethod,
            PaymentGateway paymentGateway,
            BigDecimal amount,
            BigDecimal planPriceSnapshot,
            String currencyCode,
            String reason
    ) {
        Long accountId = requestIdentity.getCurrentAccountId();

        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        }

        log.info("Tenant solicitando mudança de plano. accountId={}, targetPlan={}", accountId, targetPlan);

        return orchestrationService.execute(
                accountId,
                targetPlan,
                billingCycle,
                paymentMethod,
                paymentGateway,
                amount,
                planPriceSnapshot,
                currencyCode,
                reason
        );
    }
}