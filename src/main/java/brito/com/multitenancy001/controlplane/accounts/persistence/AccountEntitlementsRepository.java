package brito.com.multitenancy001.controlplane.accounts.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import brito.com.multitenancy001.controlplane.accounts.domain.AccountEntitlements;

import java.util.Optional;

public interface AccountEntitlementsRepository extends JpaRepository<AccountEntitlements, Long> {

    Optional<AccountEntitlements> findByAccount_Id(Long accountId);

    /**
     * Upsert/Idempotente no estilo do V13:
     * - insere default
     * - se já existir, não faz nada
     *
     * Retorna 1 se inseriu, 0 se já existia.
     *
     * IMPORTANTE:
     * Deve ser executado dentro de uma TX write-capable (via PublicUnitOfWork.tx()).
     */
    @Modifying
    @Query(
        value = """
            INSERT INTO public.account_entitlements (
                account_id, max_users, max_products, max_storage_mb, created_at, updated_at
            )
            VALUES (
                :accountId, :maxUsers, :maxProducts, :maxStorageMb, now(), now()
            )
            ON CONFLICT (account_id) DO NOTHING
            """,
        nativeQuery = true
    )
    int insertDefaultIfMissing(
            @Param("accountId") Long accountId,
            @Param("maxUsers") Integer maxUsers,
            @Param("maxProducts") Integer maxProducts,
            @Param("maxStorageMb") Integer maxStorageMb
    );
}
