package brito.com.multitenancy001.controlplane.accounts.app.subscription;

import java.util.Optional;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.domain.AccountUsageSnapshot;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountUsageSnapshotRepository;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de leitura do snapshot materializado de uso da conta.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Ler snapshots de uso no schema public.</li>
 *   <li>Centralizar a regra de ausência de snapshot.</li>
 *   <li>Evitar que os consumidores leiam o repository diretamente.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountUsageSnapshotQueryService {

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final AccountUsageSnapshotRepository accountUsageSnapshotRepository;

    /**
     * Busca o snapshot de uso da conta, se existir.
     *
     * @param accountId id da conta
     * @return snapshot opcional
     */
    public Optional<AccountUsageSnapshot> findByAccountId(Long accountId) {
        SubscriptionValidator.requireAccountId(accountId);

        return publicSchemaUnitOfWork.readOnly(() ->
                accountUsageSnapshotRepository.findByAccountId(accountId)
        );
    }

    /**
     * Exige a existência do snapshot de uso da conta.
     *
     * @param accountId id da conta
     * @return snapshot materializado
     */
    public AccountUsageSnapshot requireByAccountId(Long accountId) {
        SubscriptionValidator.requireAccountId(accountId);

        AccountUsageSnapshot snapshot = publicSchemaUnitOfWork.readOnly(() ->
                accountUsageSnapshotRepository.findByAccountId(accountId)
                        .orElseThrow(() -> new ApiException(
                                ApiErrorCode.INVALID_REQUEST,
                                "Snapshot de uso da conta ainda não está disponível para accountId=" + accountId
                        ))
        );

        log.info(
                "Snapshot de uso carregado com sucesso. accountId={}, currentUsers={}, currentProducts={}, currentStorageMb={}, measuredAt={}",
                snapshot.getAccountId(),
                snapshot.getCurrentUsers(),
                snapshot.getCurrentProducts(),
                snapshot.getCurrentStorageMb(),
                snapshot.getMeasuredAt()
        );

        return snapshot;
    }

    /**
     * Informa se existe snapshot materializado para a conta.
     *
     * @param accountId id da conta
     * @return true se existir
     */
    public boolean existsByAccountId(Long accountId) {
        SubscriptionValidator.requireAccountId(accountId);

        return publicSchemaUnitOfWork.readOnly(() ->
                accountUsageSnapshotRepository.existsByAccountId(accountId)
        );
    }
}