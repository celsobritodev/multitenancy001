package brito.com.multitenancy001.tenant.subscription.api.dto;

import java.util.List;

/**
 * Response consolidado de limites/uso da assinatura do Tenant.
 *
 * @param accountId id da conta
 * @param accountStatus status atual da conta
 * @param currentPlan plano atual
 * @param maxUsers limite de usuários
 * @param maxProducts limite de produtos
 * @param maxStorageMb limite de storage
 * @param unlimited indica plano ilimitado
 * @param currentUsers uso atual de usuários
 * @param currentProducts uso atual de produtos
 * @param currentStorageMb uso atual de storage
 * @param remainingUsers saldo restante de usuários
 * @param remainingProducts saldo restante de produtos
 * @param remainingStorageMb saldo restante de storage
 * @param eligibleDowngrades planos inferiores elegíveis
 * @param blockedDowngrades planos inferiores bloqueados
 * @param availableUpgrades planos superiores possíveis
 */
public record TenantPlanLimitsResponse(
        Long accountId,
        String accountStatus,
        String currentPlan,
        int maxUsers,
        int maxProducts,
        int maxStorageMb,
        boolean unlimited,
        long currentUsers,
        long currentProducts,
        long currentStorageMb,
        long remainingUsers,
        long remainingProducts,
        long remainingStorageMb,
        List<String> eligibleDowngrades,
        List<String> blockedDowngrades,
        List<String> availableUpgrades
) {
}