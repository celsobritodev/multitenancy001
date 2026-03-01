package brito.com.multitenancy001.controlplane.accounts.app;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@ConfigurationProperties(prefix = "app.entitlements")
public class EntitlementsProperties {

    private int devMultiplier = 1;

    /**
     * Mapa: "free", "pro", "enterprise" -> limites
     */
    private Map<String, PlanLimits> plans;

    public int getDevMultiplier() {
        return devMultiplier;
    }

    public void setDevMultiplier(int devMultiplier) {
        this.devMultiplier = devMultiplier;
    }

    public Map<String, PlanLimits> getPlans() {
        return plans;
    }

    public void setPlans(Map<String, PlanLimits> plans) {
        this.plans = plans;
    }

    public static class PlanLimits {
        private int maxUsers;
        private int maxProducts;
        private int maxStorageMb;

        public int getMaxUsers() {
            return maxUsers;
        }

        public void setMaxUsers(int maxUsers) {
            this.maxUsers = maxUsers;
        }

        public int getMaxProducts() {
            return maxProducts;
        }

        public void setMaxProducts(int maxProducts) {
            this.maxProducts = maxProducts;
        }

        public int getMaxStorageMb() {
            return maxStorageMb;
        }

        public void setMaxStorageMb(int maxStorageMb) {
            this.maxStorageMb = maxStorageMb;
        }
    }

    /**
     * Fail-fast global (config).
     */
    public void validate() {
        if (devMultiplier <= 0) {
            throw new ApiException(ApiErrorCode.INVALID_ENTITLEMENT, "devMultiplier invalido", 500);
        }
        if (plans == null || plans.isEmpty()) {
            throw new ApiException(ApiErrorCode.INVALID_ENTITLEMENT, "plans não configurado", 500);
        }
        for (Map.Entry<String, PlanLimits> e : plans.entrySet()) {
            String plan = e.getKey();
            PlanLimits l = e.getValue();
            if (l == null) {
                throw new ApiException(ApiErrorCode.INVALID_ENTITLEMENT, "plan " + plan + " invalido: null", 500);
            }
            if (l.getMaxUsers() <= 0) throw new ApiException(ApiErrorCode.INVALID_ENTITLEMENT, "plan " + plan + " maxUsers invalido", 500);
            if (l.getMaxProducts() <= 0) throw new ApiException(ApiErrorCode.INVALID_ENTITLEMENT, "plan " + plan + " maxProducts invalido", 500);
            if (l.getMaxStorageMb() <= 0) throw new ApiException(ApiErrorCode.INVALID_ENTITLEMENT, "plan " + plan + " maxStorageMb invalido", 500);
        }
    }

    /**
     * Fail-fast por plano.
     */
    public PlanLimits getPlanOrThrow(String planKey) {
        validate();

        if (planKey == null || planKey.isBlank()) {
            throw new ApiException(ApiErrorCode.INVALID_ENTITLEMENT, "planKey obrigatorio", 500);
        }

        PlanLimits limits = plans.get(planKey.toLowerCase());
        if (limits == null) {
            throw new ApiException(ApiErrorCode.INVALID_ENTITLEMENT, "plano não configurado: " + planKey, 500);
        }
        return limits;
    }
}