package brito.com.multitenancy001.tenant.subscription.api.dto;

import java.util.List;

/**
 * Response de preview de mudança de plano para o Tenant.
 *
 * @param currentPlan plano atual
 * @param targetPlan plano alvo
 * @param changeType tipo da mudança
 * @param eligible indica se é elegível
 * @param currentUsers uso atual de usuários
 * @param currentProducts uso atual de produtos
 * @param currentStorageMb uso atual de storage
 * @param targetMaxUsers limite alvo de usuários
 * @param targetMaxProducts limite alvo de produtos
 * @param targetMaxStorageMb limite alvo de storage
 * @param targetUnlimited indica se o plano alvo é ilimitado
 * @param violations violações encontradas
 */
public record TenantPlanChangePreviewResponse(
        String currentPlan,
        String targetPlan,
        String changeType,
        boolean eligible,
        long currentUsers,
        long currentProducts,
        long currentStorageMb,
        int targetMaxUsers,
        int targetMaxProducts,
        int targetMaxStorageMb,
        boolean targetUnlimited,
        List<TenantPlanViolationResponse> violations
) {
}