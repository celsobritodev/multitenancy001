package brito.com.multitenancy001.shared.contracts;

import java.util.List;

/**
 * Contrato (porta) que define as operações que o Control Plane pode executar
 * sobre os usuários de um Tenant.
 *
 * Esta interface vive no módulo 'shared' e representa uma dependência invertida:
 * o Control Plane depende desta abstração, e não de uma implementação concreta do Tenant.
 *
 * A implementação concreta (adaptador) deve ser fornecida pelo módulo 'integration.tenant',
 * que conhece os detalhes de como executar as operações no contexto do Tenant,
 * incluindo a troca de schema via TenantExecutor.
 *
 * Regras de implementação:
 * - Os métodos devem ser idempotentes sempre que possível.
 * - As implementações devem lidar com a troca de schema (tenantSchema) internamente.
 * - Exceções devem ser do tipo ApiException com os códigos apropriados.
 *
 * @see brito.com.multitenancy001.integration.tenant.TenantProvisioningIntegrationService
 */
public interface TenantUserOperations {

    /**
     * Lista os usuários de um tenant.
     *
     * @param tenantSchema    O schema do tenant alvo (obrigatório).
     * @param accountId       O ID da conta (obrigatório).
     * @param onlyOperational Se true, retorna apenas usuários operacionais
     *                        (não deletados e não suspensos por account/admin).
     * @return Uma lista de resumos dos usuários (nunca null).
     * @throws brito.com.multitenancy001.shared.kernel.error.ApiException
     *         Se o tenantSchema for inválido, a conta não for encontrada,
     *         ou o schema do tenant não estiver pronto.
     */
    List<UserSummaryData> listUserSummaries(String tenantSchema, Long accountId, boolean onlyOperational);

    /**
     * Define o status de suspensão por admin de um usuário específico.
     *
     * @param tenantSchema O schema do tenant alvo (obrigatório).
     * @param accountId    O ID da conta (obrigatório).
     * @param userId       O ID do usuário alvo (obrigatório).
     * @param suspended    O novo status de suspensão (true = suspenso, false = reativado).
     * @throws brito.com.multitenancy001.shared.kernel.error.ApiException
     *         Se os parâmetros forem inválidos, o usuário não for encontrado,
     *         ou a operação violar regras de negócio (ex: suspender o último owner).
     */
    void setUserSuspendedByAdmin(String tenantSchema, Long accountId, Long userId, boolean suspended);

    /**
     * Suspende todos os usuários de uma conta, exceto o TENANT_OWNER.
     *
     * @param tenantSchema O schema do tenant alvo (obrigatório).
     * @param accountId    O ID da conta (obrigatório).
     * @return O número de usuários afetados pela operação.
     * @throws brito.com.multitenancy001.shared.kernel.error.ApiException
     *         Se a conta não existir ou se não houver pelo menos um TENANT_OWNER ativo.
     */
    int suspendAllUsersByAccount(String tenantSchema, Long accountId);

    /**
     * Reativa (remove suspensão por account) todos os usuários de uma conta.
     *
     * @param tenantSchema O schema do tenant alvo (obrigatório).
     * @param accountId    O ID da conta (obrigatório).
     * @return O número de usuários afetados pela operação.
     * @throws brito.com.multitenancy001.shared.kernel.error.ApiException
     *         Se a conta não for encontrada.
     */
    int unsuspendAllUsersByAccount(String tenantSchema, Long accountId);

    /**
     * Aplica soft delete em todos os usuários de uma conta, exceto o TENANT_OWNER.
     *
     * @param tenantSchema O schema do tenant alvo (obrigatório).
     * @param accountId    O ID da conta (obrigatório).
     * @return O número de usuários afetados pela operação.
     * @throws brito.com.multitenancy001.shared.kernel.error.ApiException
     *         Se a conta não existir ou se não houver pelo menos um TENANT_OWNER ativo.
     */
    int softDeleteAllUsersByAccount(String tenantSchema, Long accountId);

    /**
     * Restaura todos os usuários de uma conta que foram previamente deletados.
     *
     * @param tenantSchema O schema do tenant alvo (obrigatório).
     * @param accountId    O ID da conta (obrigatório).
     * @return O número de usuários afetados pela operação.
     * @throws brito.com.multitenancy001.shared.kernel.error.ApiException
     *         Se a conta não for encontrada.
     */
    int restoreAllUsersByAccount(String tenantSchema, Long accountId);
}