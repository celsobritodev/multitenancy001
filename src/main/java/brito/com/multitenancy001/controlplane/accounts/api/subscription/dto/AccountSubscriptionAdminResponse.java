package brito.com.multitenancy001.controlplane.accounts.api.subscription.dto;

import java.util.List;

/**
 * Response consolidado de assinatura/limites/uso para o Control Plane.
 *
 * @param accountId id da conta
 * @param accountStatus status atual
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
 * @param eligibleDowngrades downgrades elegíveis
 * @param blockedDowngrades downgrades bloqueados
 * @param availableUpgrades upgrades possíveis
 */
public record AccountSubscriptionAdminResponse(
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