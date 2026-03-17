package brito.com.multitenancy001.controlplane.accounts.api.subscription.dto;

import brito.com.multitenancy001.controlplane.accounts.domain.SubscriptionPlan;
import jakarta.validation.constraints.NotNull;

/**
 * Request de preview de mudança de plano no Control Plane.
 *
 * @param targetPlan plano alvo
 */
public record AccountPlanChangePreviewRequest(
        @NotNull(message = "targetPlan é obrigatório")
        SubscriptionPlan targetPlan
) {
}