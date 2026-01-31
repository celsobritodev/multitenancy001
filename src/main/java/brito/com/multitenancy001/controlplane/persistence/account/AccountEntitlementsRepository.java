package brito.com.multitenancy001.controlplane.persistence.account;

import brito.com.multitenancy001.controlplane.domain.account.AccountEntitlements;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountEntitlementsRepository extends JpaRepository<AccountEntitlements, Long> {

    boolean existsByAccount_Id(Long accountId);

    Optional<AccountEntitlements> findByAccount_Id(Long accountId);
}
