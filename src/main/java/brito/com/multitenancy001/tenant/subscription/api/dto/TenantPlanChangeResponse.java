package brito.com.multitenancy001.tenant.subscription.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response da mudança efetiva de plano no contexto do Tenant.
 *
 * <p>Semântica:</p>
 * <ul>
 *   <li>Para downgrade aplicado, payment* pode vir nulo.</li>
 *   <li>Para upgrade processado via billing, os metadados do pagamento retornam preenchidos.</li>
 *   <li>targetPlan representa o plano solicitado.</li>
 *   <li>newPlan representa o plano efetivamente vigente após o processamento.</li>
 * </ul>
 *
 * @param accountId id da conta
 * @param oldPlan plano anterior
 * @param newPlan plano efetivamente vigente após o fluxo
 * @param targetPlan plano solicitado
 * @param changeType tipo da mudança
 * @param eligible indica se o preview era elegível
 * @param paymentRequired indica se o fluxo passou por billing
 * @param paymentId id do pagamento criado/processado
 * @param paymentStatus status final do pagamento
 * @param paymentMethod método utilizado
 * @param paymentGateway gateway utilizado
 * @param billingCycle ciclo de cobrança
 * @param amount valor cobrado
 * @param currencyCode moeda
 * @param effectiveFrom início efetivo da cobertura
 * @param coverageEndDate fim da cobertura
 * @param message mensagem funcional
 */
public record TenantPlanChangeResponse(
        Long accountId,
        String oldPlan,
        String newPlan,
        String targetPlan,
        String changeType,
        boolean eligible,
        boolean paymentRequired,
        Long paymentId,
        String paymentStatus,
        String paymentMethod,
        String paymentGateway,
        String billingCycle,
        BigDecimal amount,
        String currencyCode,
        Instant effectiveFrom,
        Instant coverageEndDate,
        String message
) {
}