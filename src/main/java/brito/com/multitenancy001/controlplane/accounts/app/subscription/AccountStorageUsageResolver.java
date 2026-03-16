package brito.com.multitenancy001.controlplane.accounts.app.subscription;

/**
 * Contrato interno simples para resolver o consumo atual de storage de uma conta.
 *
 * <p>Motivação:</p>
 * <ul>
 *   <li>Manter a lógica de uso de storage desacoplada do serviço de mudança de plano</li>
 *   <li>Permitir começar com implementação conservadora sem travar o restante da feature</li>
 *   <li>Facilitar futura troca por cálculo real consolidado</li>
 * </ul>
 *
 * <p>Importante:</p>
 * <ul>
 *   <li>Não é port/adapter; é apenas um serviço interno de aplicação</li>
 *   <li>Permanece no mesmo estilo layered do projeto</li>
 * </ul>
 */
public interface AccountStorageUsageResolver {

    /**
     * Resolve o consumo atual de armazenamento da conta.
     *
     * @param accountId id da conta
     * @return storage consumido em MB
     */
    long resolveStorageMb(Long accountId);
}