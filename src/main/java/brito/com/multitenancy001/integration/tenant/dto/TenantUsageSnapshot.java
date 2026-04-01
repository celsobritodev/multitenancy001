package brito.com.multitenancy001.integration.tenant.dto;

/**
 * Snapshot de uso exposto pela camada de integração tenant
 * para consumo por outros contextos, especialmente o Control Plane.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Representar o uso atual do tenant em formato simples e neutro.</li>
 *   <li>Evitar que o contexto consumidor dependa de repositories
 *       ou services internos do Tenant.</li>
 * </ul>
 *
 * @param currentUsers quantidade atual de usuários habilitados
 * @param currentProducts quantidade atual de produtos não deletados
 */
public record TenantUsageSnapshot(
        long currentUsers,
        long currentProducts
) {
}