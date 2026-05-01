package brito.com.multitenancy001.shared.persistence.publicschema;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.app.AccountEntitlementsProvisioningService;
import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.domain.AccountEntitlements;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountEntitlementsRepository;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.validation.RequiredValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountEntitlementsService {

    private final AccountEntitlementsRepository accountEntitlementsRepository;
    private final AccountEntitlementsProvisioningService provisioningService;
    private final AccountRepository accountRepository;
    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;

    public AccountEntitlementsSnapshot resolveEffective(Account account) {

        RequiredValidator.requirePayload(
                account,
                ApiErrorCode.ACCOUNT_REQUIRED,
                "Conta é obrigatória"
        );

        RequiredValidator.requirePayload(
                account.getId(),
                ApiErrorCode.ACCOUNT_REQUIRED,
                "accountId é obrigatório"
        );

        log.info(
                "Resolvendo entitlements efetivos por conta. accountId={}, plan={}, builtIn={}",
                account.getId(),
                account.getSubscriptionPlan(),
                account.isBuiltInAccount()
        );

        AccountEntitlementsSnapshot snapshot =
                publicSchemaUnitOfWork.tx(() -> resolveEffectiveInternal(account));

        log.info(
                "Entitlements efetivos resolvidos com sucesso. accountId={}, unlimited={}, maxUsers={}, maxProducts={}, maxStorageMb={}",
                account.getId(),
                snapshot.unlimited(),
                snapshot.maxUsers(),
                snapshot.maxProducts(),
                snapshot.maxStorageMb()
        );

        return snapshot;
    }

    public AccountEntitlementsSnapshot resolveEffectiveByAccountId(Long accountId) {

        RequiredValidator.requirePayload(
                accountId,
                ApiErrorCode.ACCOUNT_REQUIRED,
                "accountId é obrigatório"
        );

        log.info("Resolvendo entitlements efetivos por accountId. accountId={}", accountId);

        AccountEntitlementsSnapshot snapshot = publicSchemaUnitOfWork.tx(() -> {

            Account account = accountRepository.findByIdAndDeletedFalse(accountId)
                    .orElseThrow(() -> new ApiException(
                            ApiErrorCode.ACCOUNT_NOT_FOUND,
                            "Conta não encontrada"
                    ));

            return resolveEffectiveInternal(account);
        });

        log.info(
                "Entitlements efetivos resolvidos por accountId com sucesso. accountId={}, unlimited={}, maxUsers={}, maxProducts={}, maxStorageMb={}",
                accountId,
                snapshot.unlimited(),
                snapshot.maxUsers(),
                snapshot.maxProducts(),
                snapshot.maxStorageMb()
        );

        return snapshot;
    }

    public void assertCanCreateUser(Account account, long currentUsers) {
        validateNonNegativeUsage(currentUsers, "currentUsers");

        AccountEntitlementsSnapshot eff = resolveEffective(account);

        if (eff.unlimited()) {
            return;
        }

        if (currentUsers >= eff.maxUsers()) {
            throw new ApiException(
                    ApiErrorCode.QUOTA_MAX_USERS_REACHED,
                    "Limite de usuários atingido para este plano"
            );
        }
    }

    public void assertCanCreateProduct(Account account, long currentProducts) {
        validateNonNegativeUsage(currentProducts, "currentProducts");

        AccountEntitlementsSnapshot eff = resolveEffective(account);

        if (eff.unlimited()) {
            return;
        }

        if (currentProducts >= eff.maxProducts()) {
            throw new ApiException(
                    ApiErrorCode.QUOTA_MAX_PRODUCTS_REACHED,
                    "Limite de produtos atingido para este plano"
            );
        }
    }

    public void assertCanConsumeStorage(Account account, long currentStorageMb, long deltaMb) {

        validateNonNegativeUsage(currentStorageMb, "currentStorageMb");

        if (deltaMb < 0) {
            throw new ApiException(
                    ApiErrorCode.INVALID_STORAGE_DELTA,
                    "deltaMb não pode ser negativo"
            );
        }

        AccountEntitlementsSnapshot eff = resolveEffective(account);

        if (eff.unlimited()) {
            return;
        }

        long after = currentStorageMb + deltaMb;

        if (after > eff.maxStorageMb()) {
            throw new ApiException(
                    ApiErrorCode.QUOTA_MAX_STORAGE_REACHED,
                    "Limite de armazenamento atingido para este plano"
            );
        }
    }

    private AccountEntitlementsSnapshot resolveEffectiveInternal(Account account) {

        if (account.isBuiltInAccount()) {
            return AccountEntitlementsSnapshot.ofUnlimited();
        }

        AccountEntitlements ent = accountEntitlementsRepository
                .findByAccount_Id(account.getId())
                .orElse(null);

        if (ent == null) {
            ent = provisioningService.ensureDefaultEntitlementsForTenant(account);
        }

        if (ent == null) {
            throw new ApiException(
                    ApiErrorCode.ENTITLEMENTS_UNEXPECTED_NULL,
                    "Entitlements inesperadamente nulos"
            );
        }

        Integer maxUsers = safePositive(ent.getMaxUsers(), "maxUsers", account.getId());
        Integer maxProducts = safePositive(ent.getMaxProducts(), "maxProducts", account.getId());
        Integer maxStorageMb = safePositive(ent.getMaxStorageMb(), "maxStorageMb", account.getId());

        return AccountEntitlementsSnapshot.ofLimited(maxUsers, maxProducts, maxStorageMb);
    }

    private Integer safePositive(Integer value, String field, Long accountId) {
        if (value == null || value <= 0) {
            throw new ApiException(
                    ApiErrorCode.INVALID_ENTITLEMENT,
                    "Entitlement inválido: " + field
            );
        }
        return value;
    }

    private void validateNonNegativeUsage(long usage, String field) {
        if (usage < 0) {
            throw new ApiException(
                    ApiErrorCode.INVALID_REQUEST,
                    field + " não pode ser negativo"
            );
        }
    }
}