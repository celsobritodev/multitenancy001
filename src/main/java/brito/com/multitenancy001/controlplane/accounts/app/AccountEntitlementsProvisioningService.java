package brito.com.multitenancy001.controlplane.accounts.app;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.AccountEntitlements;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountEntitlementsRepository;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AccountEntitlementsProvisioningService {

    private final AccountEntitlementsRepository accountEntitlementsRepository;
    private final AppClock appClock;

    /**
     * Garante entitlements default para TENANT (idempotente / race-safe).
     * - BUILT_IN => não persiste entitlements (ilimitado)
     * - TENANT   => INSERT ... ON CONFLICT DO NOTHING + SELECT
     *
     * IMPORTANTE:
     * Este método deve rodar dentro de uma TX write-capable (via PublicSchemaUnitOfWork.tx()).
     */
    public AccountEntitlements ensureDefaultEntitlementsForTenant(Account account) {
        if (account == null || account.getId() == null) {
            throw new ApiException("ACCOUNT_REQUIRED", "Conta é obrigatória", 400);
        }

        if (account.isBuiltInAccount()) {
            // BUILT_IN/PLATFORM => ilimitado / não precisa persistir
            return null;
        }

        // AppClock é a única fonte de tempo
        Instant now = appClock.instant();

        int inserted = accountEntitlementsRepository.insertDefaultIfMissing(
                account.getId(),
                5,
                100,
                100,
                now,
                now
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
