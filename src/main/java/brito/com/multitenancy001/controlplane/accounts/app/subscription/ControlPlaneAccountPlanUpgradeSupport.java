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
 * Support compartilhado do fluxo de upgrade no contexto do Control Plane.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Validar inputs obrigatórios do upgrade.</li>
 *   <li>Calcular cobertura recorrente.</li>
 *   <li>Montar descrição funcional.</li>
 *   <li>Gerar chave funcional de idempotência.</li>
 *   <li>Normalizar strings e moeda.</li>
 * </ul>
 */
@Component
@Slf4j
public class ControlPlaneAccountPlanUpgradeSupport {

    private static final String DEFAULT_CURRENCY = "BRL";

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
    public void validateUpgradeInputs(
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
    public Instant resolveCoverageEndDate(Instant effectiveFrom, BillingCycle billingCycle) {
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

    /**
     * Normaliza moeda informada.
     *
     * @param currencyCode moeda opcional
     * @return moeda normalizada
     */
    public String normalizeCurrency(String currencyCode) {
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
    public String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}