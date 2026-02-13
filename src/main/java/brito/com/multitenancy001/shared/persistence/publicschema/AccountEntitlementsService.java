package brito.com.multitenancy001.shared.persistence.publicschema;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;

import brito.com.multitenancy001.controlplane.accounts.app.AccountEntitlementsProvisioningService;
import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.AccountEntitlements;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountEntitlementsRepository;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountEntitlementsService {

    private final AccountEntitlementsRepository accountEntitlementsRepository;
    private final AccountEntitlementsProvisioningService provisioningService;
    private final AccountRepository accountRepository;
    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;

    /**
     * Resolve entitlements efetivos da conta.
     * ✅ TX normal porque pode provisionar default entitlements.
     */
    public AccountEntitlementsSnapshot resolveEffective(Account account) {
        return publicSchemaUnitOfWork.tx(() -> resolveEffectiveInternal(account));
    }

    /**
     * ✅ NOVO: usado na camada TENANT para expor entitlements (apenas para TENANT_OWNER)
     * Resolve entitlements efetivos por accountId.
     * ✅ TX normal porque pode provisionar default entitlements.
     */
    public AccountEntitlementsSnapshot resolveEffectiveByAccountId(Long accountId) {
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);

        return publicSchemaUnitOfWork.tx(() -> {
            Account account = accountRepository.findByIdAndDeletedFalse(accountId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.ACCOUNT_NOT_FOUND, "Conta não encontrada", 404));

            return resolveEffectiveInternal(account);
        });
    }

    // =========================================================
    // QUOTAS / ASSERTS (compatível com AccountEntitlementsGuard)
    // =========================================================

    public boolean canCreateUser(Account account, long currentUsers) {
        AccountEntitlementsSnapshot eff = resolveEffective(account);
        return currentUsers < eff.maxUsers();
    }

    public void assertCanCreateUser(Account account, long currentUsers) {
        AccountEntitlementsSnapshot eff = resolveEffective(account);
        if (currentUsers >= eff.maxUsers()) {
            throw new ApiException(ApiErrorCode.QUOTA_MAX_USERS_REACHED, "Limite de usuários atingido para este plano", 403);
        }
    }

    public boolean canCreateProduct(Account account, long currentProducts) {
        AccountEntitlementsSnapshot eff = resolveEffective(account);
        return currentProducts < eff.maxProducts();
    }

    public void assertCanCreateProduct(Account account, long currentProducts) {
        AccountEntitlementsSnapshot eff = resolveEffective(account);
        if (currentProducts >= eff.maxProducts()) {
            throw new ApiException(ApiErrorCode.QUOTA_MAX_PRODUCTS_REACHED, "Limite de produtos atingido para este plano", 403);
        }
    }

    public void assertCanConsumeStorage(Account account, long currentStorageMb, long deltaMb) {
        if (deltaMb < 0) {
            throw new ApiException(ApiErrorCode.INVALID_STORAGE_DELTA, "deltaMb não pode ser negativo", 400);
        }

        AccountEntitlementsSnapshot eff = resolveEffective(account);
        long after = currentStorageMb + deltaMb;

        if (after > eff.maxStorageMb()) {
            throw new ApiException(ApiErrorCode.QUOTA_MAX_STORAGE_REACHED, "Limite de armazenamento atingido para este plano", 403);
        }
    }

    // =========================================================
    // INTERNALS (sem tx aqui)
    // =========================================================

    private AccountEntitlementsSnapshot resolveEffectiveInternal(Account account) {
        if (account == null || account.getId() == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "Conta é obrigatória", 400);
        }

        // BUILT_IN/PLATFORM => ilimitado
        if (account.isBuiltInAccount()) {
            return AccountEntitlementsSnapshot.ofUnlimited();
        }

        AccountEntitlements ent = accountEntitlementsRepository
                .findByAccount_Id(account.getId())
                .orElse(null);

        if (ent == null) {
            // provisiona default (idempotente / race-safe)
            ent = provisioningService.ensureDefaultEntitlementsForTenant(account);
        }

        if (ent == null) {
            throw new ApiException(ApiErrorCode.ENTITLEMENTS_UNEXPECTED_NULL, "Entitlements inesperadamente nulos", 500);
        }

        Integer maxUsers = safePositive(ent.getMaxUsers(), "maxUsers");
        Integer maxProducts = safePositive(ent.getMaxProducts(), "maxProducts");
        Integer maxStorageMb = safePositive(ent.getMaxStorageMb(), "maxStorageMb");

        return AccountEntitlementsSnapshot.ofLimited(maxUsers, maxProducts, maxStorageMb);
    }

    private Integer safePositive(Integer value, String field) {
        if (value == null || value <= 0) {
            throw new ApiException(ApiErrorCode.INVALID_ENTITLEMENT, "Entitlement inválido: " + field, 500);
        }
        return value;
    }
}

