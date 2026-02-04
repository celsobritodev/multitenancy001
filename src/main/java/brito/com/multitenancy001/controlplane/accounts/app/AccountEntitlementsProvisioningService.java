package brito.com.multitenancy001.controlplane.accounts.app;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.AccountEntitlements;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountEntitlementsRepository;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountEntitlementsProvisioningService {

    private final AccountEntitlementsRepository accountEntitlementsRepository;

    /**
     * Garante entitlements default para TENANT (idempotente / race-safe).
     * - BUILT_IN => não persiste entitlements (ilimitado)
     * - TENANT   => INSERT ... ON CONFLICT DO NOTHING + SELECT
     *
     * IMPORTANTE:
     * Este método deve rodar dentro de uma TX write-capable (via PublicUnitOfWork.tx()).
     */
    public AccountEntitlements ensureDefaultEntitlementsForTenant(Account account) {
        if (account == null || account.getId() == null) {
            throw new ApiException("ACCOUNT_REQUIRED", "Conta é obrigatória", 400);
        }

        if (account.isBuiltInAccount()) {
            // BUILT_IN/PLATFORM => ilimitado / não precisa persistir
            return null;
        }

        int inserted = accountEntitlementsRepository.insertDefaultIfMissing(
                account.getId(),
                5,
                100,
                100
        );

        return accountEntitlementsRepository.findByAccount_Id(account.getId())
                .orElseThrow(() -> new ApiException(
                        "ENTITLEMENTS_NOT_FOUND",
                        "Entitlements não encontrados para a conta " + account.getId()
                                + " (inserted=" + inserted + ")",
                        500
                ));
    }
}
