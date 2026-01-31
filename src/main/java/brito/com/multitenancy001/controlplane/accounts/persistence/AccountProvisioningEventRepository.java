package brito.com.multitenancy001.controlplane.accounts.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import brito.com.multitenancy001.controlplane.accounts.domain.AccountProvisioningEvent;

public interface AccountProvisioningEventRepository extends JpaRepository<AccountProvisioningEvent, Long> {
}