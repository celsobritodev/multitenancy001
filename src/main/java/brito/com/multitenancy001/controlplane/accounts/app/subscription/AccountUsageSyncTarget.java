package brito.com.multitenancy001.controlplane.accounts.app.subscription;

/**
 * Projeção enxuta para reconciliar usage snapshots.
 *
 * @param accountId id da conta
 * @param tenantSchema schema do tenant
 */
public record AccountUsageSyncTarget(
        Long accountId,
        String tenantSchema
) {
}