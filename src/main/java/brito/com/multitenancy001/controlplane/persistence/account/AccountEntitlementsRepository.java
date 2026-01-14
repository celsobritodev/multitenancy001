package brito.com.multitenancy001.controlplane.persistence.account;

import brito.com.multitenancy001.controlplane.domain.account.AccountEntitlements;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountEntitlementsRepository extends JpaRepository<AccountEntitlements, Long> {
}
