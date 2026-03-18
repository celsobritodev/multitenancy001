package brito.com.multitenancy001.tenant.subscription.app;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.app.subscription.AccountPlanChangeResult;
import brito.com.multitenancy001.controlplane.accounts.app.subscription.AccountPlanChangeService;
import brito.com.multitenancy001.controlplane.accounts.app.subscription.ChangeAccountPlanCommand;
import brito.com.multitenancy001.controlplane.accounts.app.subscription.PlanChangePolicy;
import brito.com.multitenancy001.controlplane.accounts.app.subscription.PlanChangeType;
import brito.com.multitenancy001.controlplane.accounts.app.subscription.PlanEligibilityResult;
import brito.com.multitenancy001.controlplane.accounts.app.subscription.PlanUsageSnapshot;
import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.SubscriptionPlan;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.controlplane.billing.app.ControlPlanePaymentService;
import brito.com.multitenancy001.integration.security.TenantRequestIdentityService;
import brito.com.multitenancy001.shared.api.dto.billing.PaymentRequest;
import brito.com.multitenancy001.shared.api.dto.billing.PaymentResponse;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.domain.billing.BillingCycle;
import brito.com.multitenancy001.shared.domain.billing.PaymentGateway;
import brito.com.multitenancy001.shared.domain.billing.PaymentMethod;
import brito.com.multitenancy001.shared.domain.billing.PaymentPurpose;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.executor.TenantToPublicBridgeExecutor;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import brito.com.multitenancy001.tenant.subscription.api.dto.TenantPlanChangeResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantSubscriptionCommandService {

    private static final String DEFAULT_CURRENCY = "BRL";
    private static final String REQUESTED_BY = "tenant_owner";
    private static final String CHANGE_SOURCE = "tenant_self_service";

    private final TenantRequestIdentityService requestIdentity;
    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final TenantToPublicBridgeExecutor tenantToPublicBridgeExecutor;
    private final AccountRepository accountRepository;
    private final brito.com.multitenancy001.controlplane.accounts.app.subscription.AccountPlanUsageService accountPlanUsageService;
    private final PlanChangePolicy planChangePolicy;
    private final AccountPlanChangeService accountPlanChangeService;
    private final ControlPlanePaymentService controlPlanePaymentService;
    private final AppClock appClock;

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
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId do tenant autenticado é obrigatório", 400);
        }

        if (targetPlan == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "targetPlan é obrigatório", 400);
        }

        log.info(
                "Solicitando mudança de plano no tenant. accountId={}, targetPlan={}, billingCycle={}, paymentMethod={}, paymentGateway={}, amount={}, reason={}",
                accountId,
                targetPlan,
                billingCycle,
                paymentMethod,
                paymentGateway,
                amount,
                normalize(reason)
        );

        Account account = loadAccount(accountId);
        PlanEligibilityResult preview = preview(account, targetPlan);

        log.info(
                "Preview calculado para mudança de plano no tenant. accountId={}, currentPlan={}, targetPlan={}, changeType={}, eligible={}",
                accountId,
                preview.currentPlan(),
                preview.targetPlan(),
                preview.changeType(),
                preview.eligible()
        );

        if (preview.changeType() == PlanChangeType.NO_CHANGE) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "A conta já está no plano informado", 409);
        }

        ChangeAccountPlanCommand command = new ChangeAccountPlanCommand(
                account.getId(),
                targetPlan,
                normalize(reason),
                REQUESTED_BY,
                CHANGE_SOURCE
        );

        if (preview.changeType() == PlanChangeType.DOWNGRADE) {
            return handleDowngrade(command, preview);
        }

        return handleUpgrade(
                account,
                preview,
                billingCycle,
                paymentMethod,
                paymentGateway,
                amount,
                planPriceSnapshot,
                currencyCode,
                normalize(reason)
        );
    }

    private TenantPlanChangeResponse handleDowngrade(
            ChangeAccountPlanCommand command,
            PlanEligibilityResult preview
    ) {
        if (!preview.eligible()) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "Downgrade não elegível para a conta atual", 409);
        }

        AccountPlanChangeResult result = tenantToPublicBridgeExecutor.call(() ->
                accountPlanChangeService.applyEligibleDowngrade(command)
        );

        log.info(
                "Downgrade aplicado com sucesso no tenant. accountId={}, oldPlan={}, newPlan={}, changeType={}",
                result.accountId(),
                result.oldPlan(),
                result.newPlan(),
                result.changeType()
        );

        return new TenantPlanChangeResponse(
                result.accountId(),
                result.oldPlan().name(),
                result.newPlan().name(),
                result.newPlan().name(),
                result.changeType().name(),
                result.eligibility().eligible(),
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "Downgrade aplicado com sucesso."
        );
    }

    private TenantPlanChangeResponse handleUpgrade(
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
        validateUpgradeInputs(billingCycle, paymentMethod, paymentGateway, amount);

        Instant effectiveFrom = appClock.instant();
        Instant coverageEndDate = resolveCoverageEndDate(effectiveFrom, billingCycle);

        log.info(
                "Iniciando upgrade via billing no tenant. accountId={}, currentPlan={}, targetPlan={}, billingCycle={}, paymentMethod={}, paymentGateway={}, amount={}, effectiveFrom={}, coverageEndDate={}",
                account.getId(),
                account.getSubscriptionPlan(),
                preview.targetPlan(),
                billingCycle,
                paymentMethod,
                paymentGateway,
                amount,
                effectiveFrom,
                coverageEndDate
        );

        PaymentResponse payment = tenantToPublicBridgeExecutor.call(() ->
                controlPlanePaymentService.processPaymentForMyAccount(
                        new PaymentRequest(
                                amount,
                                paymentMethod,
                                paymentGateway,
                                buildUpgradeDescription(account, preview.targetPlan(), reason),
                                preview.targetPlan(),
                                billingCycle,
                                PaymentPurpose.PLAN_UPGRADE,
                                planPriceSnapshot,
                                normalizeCurrency(currencyCode),
                                effectiveFrom,
                                coverageEndDate
                        )
                )
        );

        log.info(
                "Upgrade via billing concluído no tenant. accountId={}, paymentId={}, paymentStatus={}, oldPlan={}, targetPlan={}",
                account.getId(),
                payment.id(),
                payment.paymentStatus(),
                account.getSubscriptionPlan(),
                preview.targetPlan()
        );

        return new TenantPlanChangeResponse(
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

    private Account loadAccount(Long accountId) {
        return tenantToPublicBridgeExecutor.call(() ->
                publicSchemaUnitOfWork.readOnly(() ->
                        accountRepository.findByIdAndDeletedFalse(accountId)
                                .orElseThrow(() -> new ApiException(ApiErrorCode.ACCOUNT_NOT_FOUND, "Conta não encontrada", 404))
                )
        );
    }

    private PlanEligibilityResult preview(Account account, SubscriptionPlan targetPlan) {
        PlanUsageSnapshot usage = accountPlanUsageService.calculateUsage(account);
        return planChangePolicy.previewChange(usage, targetPlan);
    }

    private void validateUpgradeInputs(
            BillingCycle billingCycle,
            PaymentMethod paymentMethod,
            PaymentGateway paymentGateway,
            BigDecimal amount
    ) {
        if (billingCycle == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "billingCycle é obrigatório para upgrade", 400);
        }

        if (paymentMethod == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "paymentMethod é obrigatório para upgrade", 400);
        }

        if (paymentGateway == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "paymentGateway é obrigatório para upgrade", 400);
        }

        if (amount == null || amount.signum() <= 0) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "amount deve ser maior que zero para upgrade", 400);
        }
    }

    private Instant resolveCoverageEndDate(Instant effectiveFrom, BillingCycle billingCycle) {
        if (effectiveFrom == null || billingCycle == null) {
            return null;
        }

        ZonedDateTime base = effectiveFrom.atZone(ZoneOffset.UTC);

        return switch (billingCycle) {
            case MONTHLY -> base.plusMonths(1).toInstant();
            case YEARLY -> base.plusYears(1).toInstant();
            case ONE_TIME -> effectiveFrom;
        };
    }

    private String buildUpgradeDescription(Account account, SubscriptionPlan targetPlan, String reason) {
        StringBuilder sb = new StringBuilder();
        sb.append("Upgrade de plano da conta ").append(account.getId()).append(" para ").append(targetPlan.name());

        if (reason != null && !reason.isBlank()) {
            sb.append(". reason=").append(reason.trim());
        }

        return sb.toString();
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private String normalizeCurrency(String currencyCode) {
        String normalized = normalize(currencyCode);
        return normalized == null ? DEFAULT_CURRENCY : normalized.toUpperCase();
    }
}