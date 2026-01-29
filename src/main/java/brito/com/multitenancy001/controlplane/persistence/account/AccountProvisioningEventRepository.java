package brito.com.multitenancy001.controlplane.persistence.account;

import org.springframework.data.jpa.repository.JpaRepository;

import brito.com.multitenancy001.controlplane.domain.account.AccountProvisioningEvent;

public interface AccountProvisioningEventRepository extends JpaRepository<AccountProvisioningEvent, Long> {
}