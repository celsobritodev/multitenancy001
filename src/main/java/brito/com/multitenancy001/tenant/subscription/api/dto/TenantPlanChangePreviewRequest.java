package brito.com.multitenancy001.tenant.subscription.api.dto;

import brito.com.multitenancy001.controlplane.accounts.domain.SubscriptionPlan;
import jakarta.validation.constraints.NotNull;

/**
 * Request de preview de mudança de plano no contexto do Tenant.
 *
 * @param targetPlan plano alvo desejado
 */
public record TenantPlanChangePreviewRequest(
        @NotNull(message = "targetPlan é obrigatório")
        SubscriptionPlan targetPlan
) {
}