package brito.com.multitenancy001.controlplane.accounts.app;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.AccountEntitlements;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountEntitlementsRepository;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AccountEntitlementsProvisioningService {

    /**
     * Defaults do produto (governança).
     * Mantidos aqui (application layer) para evitar “mágica” no repository.
     */
    private static final int DEFAULT_MAX_USERS = 5;
    private static final int DEFAULT_MAX_PRODUCTS = 100;
    private static final int DEFAULT_MAX_STORAGE_MB = 1024;

    private final AccountEntitlementsRepository accountEntitlementsRepository;
    private final AppClock appClock;

    /**
     * Garante entitlements default para TENANT (idempotente / race-safe).
     *
     * Regras:
     * - BUILT_IN => não persiste entitlements (ilimitado)
     * - TENANT   => INSERT ... ON CONFLICT DO NOTHING + SELECT
     *
     * IMPORTANTE:
     * Este método deve rodar dentro de uma TX write-capable (via PublicSchemaUnitOfWork.tx()).
     */
    public AccountEntitlements ensureDefaultEntitlementsForTenant(Account account) {
        if (account == null || account.getId() == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "Conta é obrigatória");
        }

        if (account.isBuiltInAccount()) {
            // BUILT_IN/PLATFORM => ilimitado / não precisa persistir entitlements
            return null;
        }

        Instant now = appClock.instant();

        int inserted = accountEntitlementsRepository.insertDefaultIfMissing(
                account.getId(),
                DEFAULT_MAX_USERS,
                DEFAULT_MAX_PRODUCTS,
                DEFAULT_MAX_STORAGE_MB,
                now,
                now
        );

        return accountEntitlementsRepository.findByAccount_Id(account.getId())
                .orElseThrow(() -> new ApiException(
                        ApiErrorCode.ENTITLEMENTS_NOT_FOUND,
                        "Entitlements não encontrados para a conta " + account.getId()
                                + " (inserted=" + inserted + ")"
                ));
    }
}
