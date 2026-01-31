package brito.com.multitenancy001.controlplane.application;

import brito.com.multitenancy001.controlplane.domain.account.Account;
import brito.com.multitenancy001.controlplane.domain.account.AccountEntitlements;
import brito.com.multitenancy001.controlplane.persistence.account.AccountEntitlementsRepository;
import brito.com.multitenancy001.shared.api.error.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
            // BUILT_IN/PLATFORM => ilimitado / não persiste entitlements
            return null;
        }

        // 1) tenta ler primeiro (caminho feliz)
        AccountEntitlements existing = accountEntitlementsRepository
                .findByAccount_Id(account.getId())
                .orElse(null);

        if (existing != null) return existing;

        // 2) tenta criar (race-safe)
        try {
            AccountEntitlements ent = AccountEntitlements.builder()
                    .account(account)
                    .maxUsers(5)
                    .maxProducts(100)
                    .maxStorageMb(100)
                    .build();

            return accountEntitlementsRepository.save(ent);

        } catch (DataIntegrityViolationException e) {
            // outra thread criou ao mesmo tempo -> lê de volta
            return accountEntitlementsRepository.findByAccount_Id(account.getId())
                    .orElseThrow(() -> new ApiException(
                            "ENTITLEMENTS_NOT_FOUND",
                            "Entitlements não encontrados para a conta " + account.getId(),
                            500
                    ));
        }
    }
}
