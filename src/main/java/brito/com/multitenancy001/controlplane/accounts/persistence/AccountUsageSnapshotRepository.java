package brito.com.multitenancy001.controlplane.accounts.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import brito.com.multitenancy001.controlplane.accounts.domain.AccountUsageSnapshot;

/**
 * Repository do snapshot materializado de uso da conta no schema public.
 */
public interface AccountUsageSnapshotRepository extends JpaRepository<AccountUsageSnapshot, Long> {

    /**
     * Busca o snapshot materializado pela conta.
     *
     * @param accountId id da conta
     * @return snapshot, se existir
     */
    Optional<AccountUsageSnapshot> findByAccountId(Long accountId);

    /**
     * Informa se já existe snapshot materializado para a conta.
     *
     * @param accountId id da conta
     * @return true se existir
     */
    boolean existsByAccountId(Long accountId);
}