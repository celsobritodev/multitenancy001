package brito.com.multitenancy001.controlplane.accounts.app.subscription;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.controlplane.accounts.api.subscription.dto.AccountPlanChangeResponse;
import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.SubscriptionPlan;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.controlplane.billing.app.ControlPlanePaymentService;
import brito.com.multitenancy001.shared.api.dto.billing.AdminPaymentRequest;
import brito.com.multitenancy001.shared.api.dto.billing.PaymentResponse;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.domain.billing.BillingCycle;
import brito.com.multitenancy001.shared.domain.billing.PaymentGateway;
import brito.com.multitenancy001.shared.domain.billing.PaymentMethod;
import brito.com.multitenancy001.shared.domain.billing.PaymentPurpose;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Orquestrador de mudança de plano no contexto do Control Plane.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Centralizar o fluxo completo de mudança de plano por accountId.</li>
 *   <li>Separar a orquestração da facade do {@link ControlPlaneAccountSubscriptionCommandService}.</li>
 *   <li>Executar preview, decidir o fluxo e delegar para os casos de uso corretos.</li>
 *   <li>Aplicar downgrade elegível imediatamente.</li>
 *   <li>Processar upgrade via billing binding.</li>
 *   <li>Gerar chave funcional de idempotência para retry-safe do upgrade.</li>
 * </ul>
 *
 * <p>Regras arquiteturais:</p>
 * <ul>
 *   <li>Não acessa controller nem request DTO HTTP.</li>
 *   <li>Não contém regra de autenticação HTTP.</li>
 *   <li>Não substitui o papel do {@link AccountPlanChangeService}; apenas orquestra.</li>
 *   <li>O cálculo de uso continua centralizado em {@link AccountPlanUsageService}.</li>
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
public class ControlPlaneAccountPlanChangeOrchestrationService {

    private static final String DEFAULT_CURRENCY = "BRL";
    private static final String CHANGE_SOURCE = "control_plane_admin";

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final AccountRepository accountRepository;
    private final AccountPlanUsageService accountPlanUsageService;
    private final PlanChangePolicy planChangePolicy;
    private final AccountPlanChangeService accountPlanChangeService;
    private final ControlPlanePaymentService controlPlanePaymentService;
    private final AppClock appClock;

    /**
     * Executa o fluxo completo de mudança de plano.
     *
     * @param accountId id da conta alvo
     * @param targetPlan plano alvo
     * @param billingCycle ciclo de cobrança para upgrade
     * @param paymentMethod método de pagamento para upgrade
     * @param paymentGateway gateway para upgrade
     * @param amount valor a cobrar no upgrade
     * @param planPriceSnapshot snapshot opcional do preço do plano
     * @param currencyCode moeda opcional
     * @param reason motivo funcional opcional
     * @param requestedBy identificador do solicitante
     * @return resposta consolidada
     */
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
        Account account = loadAccount(accountId);
        PlanEligibilityResult preview = preview(account, targetPlan);

        log.info(
                "Preview calculado para mudança de plano no control plane. accountId={}, currentPlan={}, targetPlan={}, changeType={}, eligible={}, requestedBy={}",
                accountId,
                preview.currentPlan(),
                preview.targetPlan(),
                preview.changeType(),
                preview.eligible(),
                normalize(requestedBy)
        );

        if (preview.changeType() == PlanChangeType.NO_CHANGE) {
            log.warn(
                    "Solicitação rejeitada: plano alvo igual ao atual. accountId={}, currentPlan={}, targetPlan={}, requestedBy={}",
                    accountId,
                    preview.currentPlan(),
                    preview.targetPlan(),
                    normalize(requestedBy)
            );
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "A conta já está no plano informado", 409);
        }

        ChangeAccountPlanCommand command = new ChangeAccountPlanCommand(
                accountId,
                targetPlan,
                normalize(reason),
                normalize(requestedBy),
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
     * Aplica downgrade elegível imediatamente.
     *
     * @param command comando consolidado
     * @param preview preview já calculado
     * @return resposta final
     */
    private AccountPlanChangeResponse handleDowngrade(
            ChangeAccountPlanCommand command,
            PlanEligibilityResult preview
    ) {
        if (!preview.eligible()) {
            log.warn(
                    "Downgrade rejeitado por inelegibilidade. accountId={}, currentPlan={}, targetPlan={}, changeType={}",
                    command.accountId(),
                    preview.currentPlan(),
                    preview.targetPlan(),
                    preview.changeType()
            );
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "Downgrade não elegível para a conta atual", 409);
        }

        AccountPlanChangeResult result = accountPlanChangeService.applyEligibleDowngrade(command);

        log.info(
                "Downgrade aplicado com sucesso no control plane. accountId={}, oldPlan={}, newPlan={}, changeType={}",
                result.accountId(),
                result.oldPlan(),
                result.newPlan(),
                result.changeType()
        );

        return new AccountPlanChangeResponse(
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
    private AccountPlanChangeResponse handleUpgrade(
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
                        buildUpgradeDescription(account, preview.targetPlan(), reason),
                        preview.targetPlan(),
                        billingCycle,
                        PaymentPurpose.PLAN_UPGRADE,
                        planPriceSnapshot,
                        normalizeCurrency(currencyCode),
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

    /**
     * Carrega a conta em modo readOnly.
     *
     * @param accountId id da conta
     * @return conta carregada
     */
    private Account loadAccount(Long accountId) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        }

        Account account = publicSchemaUnitOfWork.readOnly(() ->
                accountRepository.findByIdAndDeletedFalse(accountId)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.ACCOUNT_NOT_FOUND, "Conta não encontrada", 404))
        );

        log.info(
                "Conta carregada para orchestration de mudança de plano. accountId={}, currentPlan={}, status={}",
                account.getId(),
                account.getSubscriptionPlan(),
                account.getStatus()
        );

        return account;
    }

    /**
     * Executa o preview de mudança de plano.
     *
     * @param account conta alvo
     * @param targetPlan plano alvo
     * @return preview calculado
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
     * @param preview preview já calculado
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
                    "Upgrade rejeitado: billingCycle ausente. accountId={}, targetPlan={}",
                    account.getId(),
                    preview.targetPlan()
            );
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "billingCycle é obrigatório para upgrade", 400);
        }

        if (billingCycle == BillingCycle.ONE_TIME) {
            log.warn(
                    "Upgrade rejeitado: billingCycle ONE_TIME não é permitido. accountId={}, currentPlan={}, targetPlan={}",
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
                    "Upgrade rejeitado: paymentMethod ausente. accountId={}, targetPlan={}, billingCycle={}",
                    account.getId(),
                    preview.targetPlan(),
                    billingCycle
            );
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "paymentMethod é obrigatório para upgrade", 400);
        }

        if (paymentGateway == null) {
            log.warn(
                    "Upgrade rejeitado: paymentGateway ausente. accountId={}, targetPlan={}, billingCycle={}",
                    account.getId(),
                    preview.targetPlan(),
                    billingCycle
            );
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "paymentGateway é obrigatório para upgrade", 400);
        }

        if (amount == null || amount.signum() <= 0) {
            log.warn(
                    "Upgrade rejeitado: amount inválido. accountId={}, targetPlan={}, billingCycle={}, amount={}",
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
     * @param effectiveFrom início de vigência
     * @param billingCycle ciclo recorrente
     * @return data final da cobertura
     */
    private Instant resolveCoverageEndDate(Instant effectiveFrom, BillingCycle billingCycle) {
        ZonedDateTime base = ZonedDateTime.ofInstant(effectiveFrom, ZoneOffset.UTC);

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
    private String buildUpgradeDescription(Account account, SubscriptionPlan targetPlan, String reason) {
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

    /**
     * Gera a chave funcional de idempotência do upgrade administrativo.
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
                "CP-UPGRADE:%s:%s:%s:%s:%s",
                accountId,
                currentPlan != null ? currentPlan.name() : "NULL",
                targetPlan != null ? targetPlan.name() : "NULL",
                billingCycle != null ? billingCycle.name() : "NULL",
                amount != null ? amount.stripTrailingZeros().toPlainString() : "NULL"
        );
    }

    /**
     * Normaliza moeda informada.
     *
     * @param currencyCode moeda opcional
     * @return moeda normalizada
     */
    private String normalizeCurrency(String currencyCode) {
        if (!StringUtils.hasText(currencyCode)) {
            return DEFAULT_CURRENCY;
        }
        return currencyCode.trim().toUpperCase();
    }

    /**
     * Normaliza string opcional.
     *
     * @param value valor
     * @return valor normalizado
     */
    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}