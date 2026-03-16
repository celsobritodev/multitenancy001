package brito.com.multitenancy001.controlplane.accounts.app.subscription;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementação default do consumo de storage.
 *
 * <p>Estado atual do projeto:</p>
 * <ul>
 *   <li>Há proteção de quota de storage no entitlement service</li>
 *   <li>Mas o anexo não evidenciou uma fonte consolidada e única de consumo real por conta</li>
 * </ul>
 *
 * <p>Por isso, esta implementação começa em modo conservador:
 * retorna 0L e registra log explícito.
 * Depois, quando você fechar a estratégia de storage real, basta trocar esta classe.</p>
 */
@Slf4j
@Service
public class DefaultAccountStorageUsageResolver implements AccountStorageUsageResolver {

    /**
     * Resolve storage atual da conta.
     *
     * @param accountId id da conta
     * @return sempre 0 nesta primeira versão integrada
     */
    @Override
    public long resolveStorageMb(Long accountId) {
        log.debug("Storage usage fallback/default aplicado. accountId={}, storageMb={}", accountId, 0L);
        return 0L;
    }
}