package brito.com.multitenancy001.controlplane.accounts.persistence;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import brito.com.multitenancy001.controlplane.accounts.domain.AccountProvisioningEvent;
import brito.com.multitenancy001.controlplane.accounts.domain.ProvisioningStatus;

public interface AccountProvisioningEventRepository extends JpaRepository<AccountProvisioningEvent, Long> {

    Page<AccountProvisioningEvent> findByAccountId(Long accountId, Pageable pageable);

    Optional<AccountProvisioningEvent> findTopByAccountIdOrderByCreatedAtDesc(Long accountId);

    Optional<AccountProvisioningEvent> findTopByAccountIdAndStatusOrderByCreatedAtDesc(
            Long accountId,
            ProvisioningStatus status
    );
}
