package brito.com.multitenancy001.controlplane.accounts.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import brito.com.multitenancy001.controlplane.accounts.app.subscription.AccountUsageSyncTarget;
import brito.com.multitenancy001.controlplane.accounts.domain.Account;

/**
 * Repository de leitura enxuta para reconciliação de usage snapshots.
 */
public interface AccountUsageSyncQueryRepository extends JpaRepository<Account, Long> {

    /**
     * Lista contas operacionais elegíveis para sincronização de usage snapshot.
     *
     * <p>Filtra contas não deletadas e com tenant schema preenchido.</p>
     *
     * @return lista enxuta de alvos de sincronização
     */
    @Query("""
            select new brito.com.multitenancy001.controlplane.accounts.app.subscription.AccountUsageSyncTarget(
                a.id,
                a.tenantSchema
            )
            from Account a
            where a.deleted = false
              and a.tenantSchema is not null
              and trim(a.tenantSchema) <> ''
            """)
    List<AccountUsageSyncTarget> findAllUsageSyncTargets();
}