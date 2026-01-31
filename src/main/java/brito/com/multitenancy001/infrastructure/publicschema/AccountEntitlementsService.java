package brito.com.multitenancy001.infrastructure.publicschema;

import brito.com.multitenancy001.controlplane.application.AccountEntitlementsProvisioningService;
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
    private final AccountEntitlementsProvisioningService provisioningService;

    /**
     * Resolve entitlements efetivos da conta:
     * - BUILTIN/PLATFORM => ilimitado
     * - TENANT => lê de account_entitlements (se não existir, provisiona default)
     */
    @Transactional(transactionManager = "publicTransactionManager")
    public AccountEntitlementsSnapshot resolveEffective(Account account) {
        if (account == null || account.getId() == null) {
            throw new ApiException("ACCOUNT_REQUIRED", "Conta é obrigatória", 400);
        }

        if (account.isBuiltInAccount()) {
            return AccountEntitlementsSnapshot.ofUnlimited();
        }

        AccountEntitlements ent = accountEntitlementsRepository.findByAccount_Id(account.getId()).orElse(null);

        if (ent == null) {
            // ✅ provisiona automaticamente
            provisioningService.ensureDefaultEntitlementsForTenant(account);

            // ✅ garante visibilidade imediata no mesmo fluxo
            accountEntitlementsRepository.flush();

            ent = accountEntitlementsRepository.findByAccount_Id(account.getId())
                    .orElseThrow(() -> new ApiException(
                            "ENTITLEMENTS_NOT_FOUND",
                            "Entitlements não encontrados para a conta " + account.getId(),
                            500
                    ));
        }

        Integer maxUsers = safePositive(ent.getMaxUsers(), "maxUsers");
        Integer maxProducts = safePositive(ent.getMaxProducts(), "maxProducts");
        Integer maxStorageMb = safePositive(ent.getMaxStorageMb(), "maxStorageMb");

        return AccountEntitlementsSnapshot.ofLimited(maxUsers, maxProducts, maxStorageMb);
    }

    /**
     * ⚠️ Não pode ser readOnly=true, porque resolveEffective() pode provisionar.
     */
    @Transactional(transactionManager = "publicTransactionManager")
    public boolean canCreateUser(Account account, long currentUsers) {
        AccountEntitlementsSnapshot eff = resolveEffective(account);
        return currentUsers < eff.maxUsers();
    }

    /**
     * ✅ já está correto (sem readOnly)
     */
    @Transactional(transactionManager = "publicTransactionManager")
    public void assertCanCreateUser(Account account, long currentUsers) {
        AccountEntitlementsSnapshot eff = resolveEffective(account);

        if (currentUsers >= eff.maxUsers()) {
            throw new ApiException("QUOTA_MAX_USERS_REACHED", "Limite de usuários atingido para este plano", 403);
        }
    }

    /**
     * ⚠️ Também não pode ser readOnly=true
     */
    @Transactional(transactionManager = "publicTransactionManager")
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
     * ⚠️ Também não pode ser readOnly=true
     */
    @Transactional(transactionManager = "publicTransactionManager")
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
