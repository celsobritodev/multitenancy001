package brito.com.multitenancy001.controlplane.accounts.app.subscription;

import java.time.Instant;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.domain.AccountUsageSnapshot;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountUsageSnapshotRepository;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de criação/atualização do snapshot materializado de uso no schema public.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Materializar o uso atual por conta em um único registro público.</li>
 *   <li>Garantir upsert idempotente por {@code accountId}.</li>
 *   <li>Centralizar timestamps e normalização de valores.</li>
 * </ul>
 *
 * <p>Diretriz arquitetural:</p>
 * <ul>
 *   <li>Escrita no Public Schema deve ocorrer via {@link PublicSchemaUnitOfWork#tx}.</li>
 *   <li>Leituras no Public Schema devem ocorrer via {@link PublicSchemaUnitOfWork#readOnly}.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountUsageSnapshotUpsertService {

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final AccountUsageSnapshotRepository accountUsageSnapshotRepository;
    private final AppClock appClock;

    /**
     * Cria ou atualiza o snapshot público de uso da conta.
     *
     * @param accountId id da conta
     * @param currentUsers usuários atuais
     * @param currentProducts produtos atuais
     * @param currentStorageMb storage atual em MB
     * @param measuredAt instante da medição; se null, usa o clock da aplicação
     * @return snapshot persistido
     */
    public AccountUsageSnapshot upsert(
            Long accountId,
            long currentUsers,
            long currentProducts,
            long currentStorageMb,
            Instant measuredAt
    ) {
        validateInputs(accountId, currentUsers, currentProducts, currentStorageMb);

        Instant now = appClock.instant();
        Instant effectiveMeasuredAt = measuredAt != null ? measuredAt : now;

        AccountUsageSnapshot persisted = publicSchemaUnitOfWork.tx(() -> {
            AccountUsageSnapshot snapshot = accountUsageSnapshotRepository.findByAccountId(accountId)
                    .orElseGet(AccountUsageSnapshot::new);

            boolean creating = snapshot.getId() == null;

            if (creating) {
                snapshot.setAccountId(accountId);
                snapshot.setCreatedAt(now);
            }

            snapshot.setCurrentUsers(currentUsers);
            snapshot.setCurrentProducts(currentProducts);
            snapshot.setCurrentStorageMb(currentStorageMb);
            snapshot.setMeasuredAt(effectiveMeasuredAt);
            snapshot.setUpdatedAt(now);

            AccountUsageSnapshot saved = accountUsageSnapshotRepository.save(snapshot);

            log.info(
                    "Snapshot público de uso persistido com sucesso. creating={}, accountId={}, currentUsers={}, currentProducts={}, currentStorageMb={}, measuredAt={}",
                    creating,
                    saved.getAccountId(),
                    saved.getCurrentUsers(),
                    saved.getCurrentProducts(),
                    saved.getCurrentStorageMb(),
                    saved.getMeasuredAt()
            );

            return saved;
        });

        return persisted;
    }

    /**
     * Sobrecarga conveniente para upsert usando o clock da aplicação como measuredAt.
     *
     * @param accountId id da conta
     * @param currentUsers usuários atuais
     * @param currentProducts produtos atuais
     * @param currentStorageMb storage atual em MB
     * @return snapshot persistido
     */
    public AccountUsageSnapshot upsert(
            Long accountId,
            long currentUsers,
            long currentProducts,
            long currentStorageMb
    ) {
        return upsert(accountId, currentUsers, currentProducts, currentStorageMb, appClock.instant());
    }

    /**
     * Valida entradas do upsert.
     *
     * @param accountId id da conta
     * @param currentUsers usuários atuais
     * @param currentProducts produtos atuais
     * @param currentStorageMb storage atual
     */
    private void validateInputs(
            Long accountId,
            long currentUsers,
            long currentProducts,
            long currentStorageMb
    ) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        }
        if (currentUsers < 0L) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "currentUsers não pode ser negativo", 400);
        }
        if (currentProducts < 0L) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "currentProducts não pode ser negativo", 400);
        }
        if (currentStorageMb < 0L) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "currentStorageMb não pode ser negativo", 400);
        }
    }
}