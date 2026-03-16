package brito.com.multitenancy001.controlplane.accounts.app.subscription;

import brito.com.multitenancy001.controlplane.accounts.domain.SubscriptionPlan;

/**
 * Comando de mudança de plano da conta.
 *
 * @param accountId id da conta
 * @param targetPlan plano alvo
 * @param reason motivo funcional/auditável
 * @param requestedBy identificador textual do ator solicitante
 * @param source origem da solicitação
 */
public record ChangeAccountPlanCommand(
        Long accountId,
        SubscriptionPlan targetPlan,
        String reason,
        String requestedBy,
        String source
) {
}