package brito.com.multitenancy001.controlplane.accounts.app;

import brito.com.multitenancy001.controlplane.accounts.domain.SubscriptionPlan;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultEntitlements {

    private final EntitlementsProperties properties;

    public int maxUsers(SubscriptionPlan plan) {
        return resolve(plan).getMaxUsers() * properties.getDevMultiplier();
    }

    public int maxProducts(SubscriptionPlan plan) {
        return resolve(plan).getMaxProducts() * properties.getDevMultiplier();
    }

    public int maxStorageMb(SubscriptionPlan plan) {
        return resolve(plan).getMaxStorageMb() * properties.getDevMultiplier();
    }

    private EntitlementsProperties.PlanLimits resolve(SubscriptionPlan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("plan obrigatorio");
        }

        // Plano interno do sistema: ilimitado por regra
        if (plan == SubscriptionPlan.BUILT_IN_PLAN) {
            return unlimited();
        }

        // free / pro / enterprise (conforme properties)
        return properties.getPlanOrThrow(plan.name().toLowerCase());
    }

    private EntitlementsProperties.PlanLimits unlimited() {
        EntitlementsProperties.PlanLimits limits = new EntitlementsProperties.PlanLimits();
        limits.setMaxUsers(Integer.MAX_VALUE);
        limits.setMaxProducts(Integer.MAX_VALUE);
        limits.setMaxStorageMb(Integer.MAX_VALUE);
        return limits;
    }
}