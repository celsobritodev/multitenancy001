package brito.com.multitenancy001.controlplane.accounts.persistence;

import brito.com.multitenancy001.controlplane.accounts.domain.AccountEntitlements;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * Repository JPA (Public Schema): AccountEntitlements.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Buscar snapshot materializado de entitlements por conta</li>
 *   <li>Inserir default idempotente para bootstrap/provisionamento</li>
 *   <li>Atualizar snapshot materializado após sincronização de plano</li>
 * </ul>
 *
 * <p>Observações importantes:</p>
 * <ul>
 *   <li>Este repository atua no public schema</li>
 *   <li>As operações de escrita devem rodar dentro de TX write-capable</li>
 *   <li>Os instantes devem vir da camada de aplicação via AppClock</li>
 * </ul>
 */
public interface AccountEntitlementsRepository extends JpaRepository<AccountEntitlements, Long> {

    /**
     * Busca o snapshot materializado de entitlements por accountId.
     *
     * @param accountId id da conta
     * @return snapshot, se existir
     */
    Optional<AccountEntitlements> findByAccount_Id(Long accountId);

    /**
     * Verifica se já existe snapshot materializado para a conta.
     *
     * @param accountId id da conta
     * @return true se existir
     */
    boolean existsByAccount_Id(Long accountId);

    /**
     * Insere snapshot default de forma idempotente.
     *
     * <p>Regras:</p>
     * <ul>
     *   <li>Insere se não existir</li>
     *   <li>Se já existir, não faz nada</li>
     * </ul>
     *
     * @param accountId id da conta
     * @param maxUsers limite máximo de usuários
     * @param maxProducts limite máximo de produtos
     * @param maxStorageMb limite máximo de storage em MB
     * @param createdAt timestamp de criação
     * @param updatedAt timestamp de atualização
     * @return 1 se inseriu, 0 se já existia
     */
    @Modifying
    @Transactional
    @Query(
        value = """
            INSERT INTO public.account_entitlements (
                account_id, max_users, max_products, max_storage_mb, created_at, updated_at
            )
            VALUES (
                :accountId, :maxUsers, :maxProducts, :maxStorageMb, :createdAt, :updatedAt
            )
            ON CONFLICT (account_id) DO NOTHING
            """,
        nativeQuery = true
    )
    int insertDefaultIfMissing(
            @Param("accountId") Long accountId,
            @Param("maxUsers") Integer maxUsers,
            @Param("maxProducts") Integer maxProducts,
            @Param("maxStorageMb") Integer maxStorageMb,
            @Param("createdAt") Instant createdAt,
            @Param("updatedAt") Instant updatedAt
    );

    /**
     * Atualiza o snapshot materializado de entitlements da conta.
     *
     * <p>Regras:</p>
     * <ul>
     *   <li>Não altera created_at</li>
     *   <li>Atualiza apenas limites e updated_at</li>
     * </ul>
     *
     * @param accountId id da conta
     * @param maxUsers limite máximo de usuários
     * @param maxProducts limite máximo de produtos
     * @param maxStorageMb limite máximo de storage em MB
     * @param updatedAt timestamp de atualização
     * @return quantidade de linhas afetadas
     */
    @Modifying
    @Transactional
    @Query(
        value = """
            UPDATE public.account_entitlements
               SET max_users = :maxUsers,
                   max_products = :maxProducts,
                   max_storage_mb = :maxStorageMb,
                   updated_at = :updatedAt
             WHERE account_id = :accountId
            """,
        nativeQuery = true
    )
    int updateSnapshotByAccountId(
            @Param("accountId") Long accountId,
            @Param("maxUsers") Integer maxUsers,
            @Param("maxProducts") Integer maxProducts,
            @Param("maxStorageMb") Integer maxStorageMb,
            @Param("updatedAt") Instant updatedAt
    );

    /**
     * Faz upsert semântico do snapshot materializado.
     *
     * <p>Comportamento:</p>
     * <ul>
     *   <li>Tenta inserir</li>
     *   <li>Se já existir, atualiza os limites</li>
     * </ul>
     *
     * <p>Importante:
     * deve ser chamado dentro de uma TX write-capable.</p>
     *
     * @param accountId id da conta
     * @param maxUsers limite máximo de usuários
     * @param maxProducts limite máximo de produtos
     * @param maxStorageMb limite máximo de storage em MB
     * @param now instante de operação
     * @return quantidade de linhas afetadas na operação final
     */
    default int upsertSnapshot(
            Long accountId,
            Integer maxUsers,
            Integer maxProducts,
            Integer maxStorageMb,
            Instant now
    ) {
        int inserted = insertDefaultIfMissing(
                accountId,
                maxUsers,
                maxProducts,
                maxStorageMb,
                now,
                now
        );

        if (inserted > 0) {
            return inserted;
        }

        return updateSnapshotByAccountId(
                accountId,
                maxUsers,
                maxProducts,
                maxStorageMb,
                now
        );
    }
}