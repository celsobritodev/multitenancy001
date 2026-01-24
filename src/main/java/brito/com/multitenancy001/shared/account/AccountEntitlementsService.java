package brito.com.multitenancy001.shared.account;

import brito.com.multitenancy001.controlplane.domain.account.Account;
import brito.com.multitenancy001.controlplane.domain.account.AccountEntitlements;
import brito.com.multitenancy001.controlplane.persistence.account.AccountEntitlementsRepository;
import brito.com.multitenancy001.shared.api.error.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountEntitlementsService {

    private final AccountEntitlementsRepository accountEntitlementsRepository;

    /**
     * Resolve entitlements efetivos da conta:
     * - PLATFORM => ilimitado
     * - TENANT => lê de account_entitlements
     */
    @Transactional(readOnly = true, transactionManager = "publicTransactionManager")
    public AccountEntitlementsSnapshot resolveEffective(Account account) {
        if (account == null) {
            throw new ApiException("ACCOUNT_REQUIRED", "Conta é obrigatória", 400);
        }

        if (account.isBuiltInAccount()) {
            return AccountEntitlementsSnapshot.ofUnlimited();
        }

        AccountEntitlements ent = accountEntitlementsRepository.findById(account.getId())
                .orElseThrow(() -> new ApiException(
                        "ENTITLEMENTS_NOT_FOUND",
                        "Entitlements não encontrados para a conta " + account.getId(),
                        500
                ));

        Integer maxUsers = safePositive(ent.getMaxUsers(), "maxUsers");
        Integer maxProducts = safePositive(ent.getMaxProducts(), "maxProducts");
        Integer maxStorageMb = safePositive(ent.getMaxStorageMb(), "maxStorageMb");

        return AccountEntitlementsSnapshot.ofLimited(maxUsers, maxProducts, maxStorageMb);
    }

    /**
     * Valida quota para criação de usuário.
     */
    @Transactional(readOnly = true, transactionManager = "publicTransactionManager")
    public boolean canCreateUser(Account account, long currentUsers) {
        AccountEntitlementsSnapshot eff = resolveEffective(account);
        return currentUsers < eff.maxUsers();
    }

    @Transactional(readOnly = true, transactionManager = "publicTransactionManager")
    public void assertCanCreateUser(Account account, long currentUsers) {
        AccountEntitlementsSnapshot eff = resolveEffective(account);

        if (currentUsers >= eff.maxUsers()) {
            throw new ApiException(
                    "QUOTA_MAX_USERS_REACHED",
                    "Limite de usuários atingido para este plano",
                    403
            );
        }
    }

    /**
     * Valida quota para criação de produtos.
     */
    @Transactional(readOnly = true, transactionManager = "publicTransactionManager")
    public void assertCanCreateProduct(Account account, long currentProducts) {
        AccountEntitlementsSnapshot eff = resolveEffective(account);

        if (currentProducts >= eff.maxProducts()) {
            throw new ApiException(
                    "QUOTA_MAX_PRODUCTS_REACHED",
                    "Limite de produtos atingido para este plano",
                    403
            );
        }
    }

    /**
     * Valida quota de armazenamento.
     */
    @Transactional(readOnly = true, transactionManager = "publicTransactionManager")
    public void assertCanConsumeStorage(Account account, long currentStorageMb, long deltaMb) {
        if (deltaMb < 0) {
            throw new ApiException("INVALID_STORAGE_DELTA", "deltaMb não pode ser negativo", 400);
        }

        AccountEntitlementsSnapshot eff = resolveEffective(account);
        long after = currentStorageMb + deltaMb;

        if (after > eff.maxStorageMb()) {
            throw new ApiException(
                    "QUOTA_MAX_STORAGE_REACHED",
                    "Limite de armazenamento atingido para este plano",
                    403
            );
        }
    }

    // =========================
    // Helpers
    // =========================

    private Integer safePositive(Integer value, String field) {
        if (value == null || value <= 0) {
            throw new ApiException(
                    "INVALID_ENTITLEMENT",
                    "Entitlement inválido: " + field,
                    500
            );
        }
        return value;
    }
}
