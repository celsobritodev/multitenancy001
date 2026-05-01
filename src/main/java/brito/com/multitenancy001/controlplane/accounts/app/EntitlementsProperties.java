package brito.com.multitenancy001.controlplane.accounts.app;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Propriedades de entitlements dos planos.
 */
@Component
@ConfigurationProperties(prefix = "app.entitlements")
public class EntitlementsProperties {

    private int devMultiplier = 1;

    /**
     * Mapa: "free", "pro", "enterprise" -> limites.
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
     * Fail-fast global de configuração.
     */
    public void validate() {
        if (devMultiplier <= 0) {
            throw new IllegalStateException("Configuração inválida: devMultiplier deve ser maior que zero.");
        }

        if (plans == null || plans.isEmpty()) {
            throw new IllegalStateException("Configuração inválida: plans não configurado.");
        }

        for (Map.Entry<String, PlanLimits> entry : plans.entrySet()) {
            String plan = entry.getKey();
            PlanLimits limits = entry.getValue();

            if (limits == null) {
                throw new IllegalStateException("Configuração inválida: plan " + plan + " invalido: null.");
            }

            if (limits.getMaxUsers() <= 0) {
                throw new IllegalStateException("Configuração inválida: plan " + plan + " maxUsers invalido.");
            }

            if (limits.getMaxProducts() <= 0) {
                throw new IllegalStateException("Configuração inválida: plan " + plan + " maxProducts invalido.");
            }

            if (limits.getMaxStorageMb() <= 0) {
                throw new IllegalStateException("Configuração inválida: plan " + plan + " maxStorageMb invalido.");
            }
        }
    }

    /**
     * Fail-fast por plano.
     *
     * @param planKey chave do plano
     * @return limites configurados
     */
    public PlanLimits getPlanOrThrow(String planKey) {
        validate();

        if (planKey == null || planKey.isBlank()) {
            throw new IllegalStateException("Configuração inválida: planKey obrigatorio.");
        }

        PlanLimits limits = plans.get(planKey.toLowerCase());
        if (limits == null) {
            throw new IllegalStateException("Configuração inválida: plano não configurado: " + planKey);
        }

        return limits;
    }
}