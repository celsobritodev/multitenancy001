package brito.com.multitenancy001.tenant.subscription.app;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.app.subscription.AccountPlanChangeResult;
import brito.com.multitenancy001.controlplane.accounts.app.subscription.AccountPlanChangeService;
import brito.com.multitenancy001.controlplane.accounts.app.subscription.AccountPlanUsageService;
import brito.com.multitenancy001.controlplane.accounts.app.subscription.ChangeAccountPlanCommand;
import brito.com.multitenancy001.controlplane.accounts.app.subscription.PlanChangePolicy;
import brito.com.multitenancy001.controlplane.accounts.app.subscription.PlanChangeType;
import brito.com.multitenancy001.controlplane.accounts.app.subscription.PlanEligibilityResult;
import brito.com.multitenancy001.controlplane.accounts.app.subscription.PlanUsageSnapshot;
import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.SubscriptionPlan;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.controlplane.billing.app.ControlPlanePaymentService;
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

/**
 * Orquestrador de mudança de plano no contexto Tenant.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Centralizar o fluxo completo de mudança de plano no self-service do tenant.</li>
 *   <li>Separar a orquestração da facade do command service.</li>
 *   <li>Executar preview e decidir entre downgrade imediato ou upgrade com billing.</li>
 *   <li>Executar crossing explícito tenant → public via {@link TenantToPublicBridgeExecutor}.</li>
 *   <li>Gerar chave funcional de idempotência para retry-safe do upgrade.</li>
 * </ul>
 *
 * <p>Regra funcional importante:</p>
 * <ul>
 *   <li>Upgrade de plano exige ciclo recorrente.</li>
 *   <li>{@link BillingCycle#ONE_TIME} é inválido para {@link PaymentPurpose#PLAN_UPGRADE}.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenantPlanChangeOrchestrationService {

    private static final String DEFAULT_CURRENCY = "BRL";
    private static final String REQUESTED_BY = "tenant_owner";
    private static final String CHANGE_SOURCE = "tenant_self_service";

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final TenantToPublicBridgeExecutor tenantToPublicBridgeExecutor;
    private final AccountRepository accountRepository;
    private final AccountPlanUsageService accountPlanUsageService;
    private final PlanChangePolicy planChangePolicy;
    private final AccountPlanChangeService accountPlanChangeService;
    private final ControlPlanePaymentService controlPlanePaymentService;
    private final AppClock appClock;

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
        Account account = loadAccount(accountId);
        PlanEligibilityResult preview = preview(account, targetPlan);

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

    /**
     * Aplica downgrade elegível via bridge explícita para o public schema.
     *
     * @param command comando consolidado
     * @param preview preview calculado
     * @return resposta consolidada
     */
    private TenantPlanChangeResponse handleDowngrade(
            ChangeAccountPlanCommand command,
            PlanEligibilityResult preview
    ) {
        if (!preview.eligible()) {
            log.warn(
                    "Downgrade rejeitado no tenant por inelegibilidade. accountId={}, currentPlan={}, targetPlan={}, changeType={}",
                    command.accountId(),
                    preview.currentPlan(),
                    preview.targetPlan(),
                    preview.changeType()
            );
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "Downgrade não elegível", 409);
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
                true,
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
        validateUpgradeInputs(account, preview, billingCycle, paymentMethod, paymentGateway, amount);

        Instant effectiveFrom = appClock.instant();
        Instant coverageEndDate = resolveCoverageEndDate(effectiveFrom, billingCycle);
        String idempotencyKey = buildUpgradeIdempotencyKey(
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
                                buildDescription(account, preview.targetPlan(), reason),
                                preview.targetPlan(),
                                billingCycle,
                                PaymentPurpose.PLAN_UPGRADE,
                                planPriceSnapshot,
                                normalizeCurrency(currencyCode),
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

    /**
     * Carrega a conta no public schema através de crossing explícito.
     *
     * @param accountId id da conta
     * @return conta encontrada
     */
    private Account loadAccount(Long accountId) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        }

        Account account = tenantToPublicBridgeExecutor.call(() ->
                publicSchemaUnitOfWork.readOnly(() ->
                        accountRepository.findByIdAndDeletedFalse(accountId)
                                .orElseThrow(() -> new ApiException(
                                        ApiErrorCode.ACCOUNT_NOT_FOUND,
                                        "Conta não encontrada",
                                        404
                                ))
                )
        );

        log.info(
                "Conta carregada para orchestration tenant. accountId={}, currentPlan={}, status={}",
                account.getId(),
                account.getSubscriptionPlan(),
                account.getStatus()
        );

        return account;
    }

    /**
     * Calcula preview de elegibilidade.
     *
     * @param account conta alvo
     * @param targetPlan plano alvo
     * @return preview de elegibilidade
     */
    private PlanEligibilityResult preview(Account account, SubscriptionPlan targetPlan) {
        if (targetPlan == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "targetPlan é obrigatório", 400);
        }

        PlanUsageSnapshot usage = accountPlanUsageService.calculateUsage(account);
        return planChangePolicy.previewChange(usage, targetPlan);
    }

    /**
     * Valida os dados obrigatórios do upgrade.
     *
     * @param account conta alvo
     * @param preview preview calculado
     * @param billingCycle ciclo de cobrança
     * @param paymentMethod método de pagamento
     * @param paymentGateway gateway de pagamento
     * @param amount valor do upgrade
     */
    private void validateUpgradeInputs(
            Account account,
            PlanEligibilityResult preview,
            BillingCycle billingCycle,
            PaymentMethod paymentMethod,
            PaymentGateway paymentGateway,
            BigDecimal amount
    ) {
        if (billingCycle == null) {
            log.warn(
                    "Upgrade tenant rejeitado: billingCycle ausente. accountId={}, targetPlan={}",
                    account.getId(),
                    preview.targetPlan()
            );
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "billingCycle é obrigatório para upgrade", 400);
        }

        if (billingCycle == BillingCycle.ONE_TIME) {
            log.warn(
                    "Upgrade tenant rejeitado: billingCycle ONE_TIME não é permitido. accountId={}, currentPlan={}, targetPlan={}",
                    account.getId(),
                    account.getSubscriptionPlan(),
                    preview.targetPlan()
            );
            throw new ApiException(
                    ApiErrorCode.INVALID_REQUEST,
                    "billingCycle ONE_TIME não é permitido para upgrade de plano. Use MONTHLY ou YEARLY.",
                    400
            );
        }

        if (paymentMethod == null) {
            log.warn(
                    "Upgrade tenant rejeitado: paymentMethod ausente. accountId={}, targetPlan={}, billingCycle={}",
                    account.getId(),
                    preview.targetPlan(),
                    billingCycle
            );
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "paymentMethod é obrigatório para upgrade", 400);
        }

        if (paymentGateway == null) {
            log.warn(
                    "Upgrade tenant rejeitado: paymentGateway ausente. accountId={}, targetPlan={}, billingCycle={}",
                    account.getId(),
                    preview.targetPlan(),
                    billingCycle
            );
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "paymentGateway é obrigatório para upgrade", 400);
        }

        if (amount == null || amount.signum() <= 0) {
            log.warn(
                    "Upgrade tenant rejeitado: amount inválido. accountId={}, targetPlan={}, billingCycle={}, amount={}",
                    account.getId(),
                    preview.targetPlan(),
                    billingCycle,
                    amount
            );
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "amount deve ser maior que zero para upgrade", 400);
        }
    }

    /**
     * Resolve a data de término de cobertura a partir do ciclo recorrente.
     *
     * @param effectiveFrom início da vigência
     * @param billingCycle ciclo recorrente
     * @return data final da cobertura
     */
    private Instant resolveCoverageEndDate(Instant effectiveFrom, BillingCycle billingCycle) {
        ZonedDateTime base = effectiveFrom.atZone(ZoneOffset.UTC);

        return switch (billingCycle) {
            case MONTHLY -> base.plusMonths(1).toInstant();
            case YEARLY -> base.plusYears(1).toInstant();
            case ONE_TIME -> throw new ApiException(
                    ApiErrorCode.INVALID_REQUEST,
                    "billingCycle ONE_TIME não possui cobertura recorrente para upgrade de plano",
                    400
            );
        };
    }

    /**
     * Monta a descrição funcional do upgrade.
     *
     * @param account conta alvo
     * @param targetPlan plano alvo
     * @param reason motivo opcional
     * @return descrição consolidada
     */
    private String buildDescription(Account account, SubscriptionPlan targetPlan, String reason) {
        StringBuilder description = new StringBuilder()
                .append("Upgrade de plano da conta ")
                .append(account.getId())
                .append(" de ")
                .append(account.getSubscriptionPlan())
                .append(" para ")
                .append(targetPlan);

        if (reason != null) {
            description.append(". Motivo: ").append(reason);
        }

        return description.toString();
    }

    /**
     * Gera a chave funcional de idempotência do upgrade self-service.
     *
     * @param accountId conta
     * @param currentPlan plano atual
     * @param targetPlan plano alvo
     * @param billingCycle ciclo
     * @param amount valor
     * @return chave funcional
     */
    private String buildUpgradeIdempotencyKey(
            Long accountId,
            SubscriptionPlan currentPlan,
            SubscriptionPlan targetPlan,
            BillingCycle billingCycle,
            BigDecimal amount
    ) {
        return String.format(
                "TENANT-UPGRADE:%s:%s:%s:%s:%s",
                accountId,
                currentPlan != null ? currentPlan.name() : "NULL",
                targetPlan != null ? targetPlan.name() : "NULL",
                billingCycle != null ? billingCycle.name() : "NULL",
                amount != null ? amount.stripTrailingZeros().toPlainString() : "NULL"
        );
    }

    /**
     * Normaliza string opcional.
     *
     * @param value valor
     * @return valor normalizado
     */
    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * Normaliza moeda informada.
     *
     * @param currencyCode moeda opcional
     * @return moeda normalizada
     */
    private String normalizeCurrency(String currencyCode) {
        String normalized = normalize(currencyCode);
        return normalized == null ? DEFAULT_CURRENCY : normalized.toUpperCase();
    }
}