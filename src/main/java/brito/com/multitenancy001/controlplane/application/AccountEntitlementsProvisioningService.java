package brito.com.multitenancy001.controlplane.application;

import brito.com.multitenancy001.controlplane.domain.account.Account;
import brito.com.multitenancy001.controlplane.domain.account.AccountEntitlements;
import brito.com.multitenancy001.controlplane.persistence.account.AccountEntitlementsRepository;
import brito.com.multitenancy001.shared.api.error.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountEntitlementsProvisioningService {

    private final AccountEntitlementsRepository accountEntitlementsRepository;

    @Transactional(transactionManager = "publicTransactionManager")
    public AccountEntitlements ensureDefaultEntitlementsForTenant(Account account) {
        if (account == null || account.getId() == null) {
            throw new ApiException("ACCOUNT_REQUIRED", "Conta é obrigatória", 400);
        }

        if (account.isBuiltInAccount()) {
            // BUILTIN/PLATFORM => ilimitado / não precisa persistir entitlements
            return null;
        }

        if (accountEntitlementsRepository.existsByAccount_Id(account.getId())) {
            return accountEntitlementsRepository.findByAccount_Id(account.getId()).orElse(null);
        }

        AccountEntitlements ent = AccountEntitlements.builder()
                .account(account)
                .maxUsers(5)
                .maxProducts(100)
                .maxStorageMb(100)
                .build();

        return accountEntitlementsRepository.save(ent);
    }
}
