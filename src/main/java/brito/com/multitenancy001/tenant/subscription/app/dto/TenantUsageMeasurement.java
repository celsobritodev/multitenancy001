package brito.com.multitenancy001.tenant.subscription.app.dto;

/**
 * Snapshot interno de uso medido no contexto do Tenant.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Representar a quantidade atual de usuários habilitados do tenant.</li>
 *   <li>Representar a quantidade atual de produtos não deletados do tenant.</li>
 *   <li>Servir como contrato interno entre services do contexto Tenant.</li>
 * </ul>
 *
 * <p>Regras:</p>
 * <ul>
 *   <li>Este tipo não representa entidade persistida.</li>
 *   <li>Este tipo não representa DTO HTTP.</li>
 *   <li>Storage não é incluído aqui porque continua sendo resolvido
 *       no contexto do Control Plane.</li>
 * </ul>
 *
 * @param currentUsers quantidade atual de usuários habilitados
 * @param currentProducts quantidade atual de produtos não deletados
 */
public record TenantUsageMeasurement(
        long currentUsers,
        long currentProducts
) {
}