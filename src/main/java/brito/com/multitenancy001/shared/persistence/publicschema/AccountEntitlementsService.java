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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de resolução e enforcement de entitlements da conta no PUBLIC schema.
 *
 * <p><b>Responsabilidades:</b></p>
 * <ul>
 *   <li>Resolver o snapshot efetivo de entitlements da conta.</li>
 *   <li>Provisionar defaults de forma idempotente quando necessário.</li>
 *   <li>Executar asserts de quota para usuários, produtos e storage.</li>
 *   <li>Centralizar logs diagnósticos para troubleshooting de hard limits.</li>
 * </ul>
 *
 * <p><b>Regras arquiteturais:</b></p>
 * <ul>
 *   <li>A resolução efetiva roda em contexto PUBLIC porque pode provisionar defaults.</li>
 *   <li>Conta built-in/plataforma é tratada como ilimitada.</li>
 *   <li>Para contas limitadas, os entitlements devem ser estritamente positivos.</li>
 *   <li>Os asserts devem respeitar explicitamente a flag {@code unlimited}.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountEntitlementsService {

    private final AccountEntitlementsRepository accountEntitlementsRepository;
    private final AccountEntitlementsProvisioningService provisioningService;
    private final AccountRepository accountRepository;
    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;

    /**
     * Resolve o snapshot efetivo de entitlements para a conta informada.
     *
     * <p>Este método executa em transação PUBLIC normal porque pode precisar
     * provisionar defaults de forma idempotente.</p>
     *
     * @param account conta alvo
     * @return snapshot efetivo de entitlements
     */
    public AccountEntitlementsSnapshot resolveEffective(Account account) {
        if (account == null || account.getId() == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "Conta é obrigatória", 400);
        }

        log.info(
                "Resolvendo entitlements efetivos por conta. accountId={}, plan={}, builtIn={}",
                account.getId(),
                account.getSubscriptionPlan(),
                account.isBuiltInAccount()
        );

        AccountEntitlementsSnapshot snapshot = publicSchemaUnitOfWork.tx(() -> resolveEffectiveInternal(account));

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

    /**
     * Resolve o snapshot efetivo de entitlements por id da conta.
     *
     * @param accountId id da conta
     * @return snapshot efetivo de entitlements
     */
    public AccountEntitlementsSnapshot resolveEffectiveByAccountId(Long accountId) {
        if (accountId == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        }

        log.info("Resolvendo entitlements efetivos por accountId. accountId={}", accountId);

        AccountEntitlementsSnapshot snapshot = publicSchemaUnitOfWork.tx(() -> {
            Account account = accountRepository.findByIdAndDeletedFalse(accountId)
                    .orElseThrow(() -> new ApiException(
                            ApiErrorCode.ACCOUNT_NOT_FOUND,
                            "Conta não encontrada",
                            404
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

    /**
     * Indica se a conta ainda pode criar usuário com base no uso atual medido.
     *
     * @param account conta alvo
     * @param currentUsers quantidade atual de usuários
     * @return {@code true} quando a criação é permitida
     */
    public boolean canCreateUser(Account account, long currentUsers) {
        validateNonNegativeUsage(currentUsers, "currentUsers");

        AccountEntitlementsSnapshot eff = resolveEffective(account);

        if (eff.unlimited()) {
            log.info(
                    "Criação de usuário permitida por entitlements ilimitados. accountId={}, currentUsers={}",
                    account.getId(),
                    currentUsers
            );
            return true;
        }

        boolean allowed = currentUsers < eff.maxUsers();

        log.info(
                "Avaliação de quota de usuários concluída. accountId={}, currentUsers={}, maxUsers={}, allowed={}",
                account.getId(),
                currentUsers,
                eff.maxUsers(),
                allowed
        );

        return allowed;
    }

    /**
     * Valida se a conta pode criar usuário com base no uso atual medido.
     *
     * @param account conta alvo
     * @param currentUsers quantidade atual de usuários
     */
    public void assertCanCreateUser(Account account, long currentUsers) {
        validateNonNegativeUsage(currentUsers, "currentUsers");

        AccountEntitlementsSnapshot eff = resolveEffective(account);

        if (eff.unlimited()) {
            log.info(
                    "Assert de quota de usuários liberado por plano ilimitado. accountId={}, currentUsers={}",
                    account.getId(),
                    currentUsers
            );
            return;
        }

        log.info(
                "Avaliando hard limit de usuários. accountId={}, currentUsers={}, maxUsers={}",
                account.getId(),
                currentUsers,
                eff.maxUsers()
        );

        if (currentUsers >= eff.maxUsers()) {
            log.warn(
                    "Bloqueando criação de usuário por hard limit. accountId={}, currentUsers={}, maxUsers={}",
                    account.getId(),
                    currentUsers,
                    eff.maxUsers()
            );

            throw new ApiException(
                    ApiErrorCode.QUOTA_MAX_USERS_REACHED,
                    "Limite de usuários atingido para este plano",
                    403
            );
        }

        log.info(
                "Assert de quota de usuários aprovado. accountId={}, currentUsers={}, maxUsers={}",
                account.getId(),
                currentUsers,
                eff.maxUsers()
        );
    }

    /**
     * Indica se a conta ainda pode criar produto com base no uso atual medido.
     *
     * @param account conta alvo
     * @param currentProducts quantidade atual de produtos
     * @return {@code true} quando a criação é permitida
     */
    public boolean canCreateProduct(Account account, long currentProducts) {
        validateNonNegativeUsage(currentProducts, "currentProducts");

        AccountEntitlementsSnapshot eff = resolveEffective(account);

        if (eff.unlimited()) {
            log.info(
                    "Criação de produto permitida por entitlements ilimitados. accountId={}, currentProducts={}",
                    account.getId(),
                    currentProducts
            );
            return true;
        }

        boolean allowed = currentProducts < eff.maxProducts();

        log.info(
                "Avaliação de quota de produtos concluída. accountId={}, currentProducts={}, maxProducts={}, allowed={}",
                account.getId(),
                currentProducts,
                eff.maxProducts(),
                allowed
        );

        return allowed;
    }

    /**
     * Valida se a conta pode criar produto com base no uso atual medido.
     *
     * @param account conta alvo
     * @param currentProducts quantidade atual de produtos
     */
    public void assertCanCreateProduct(Account account, long currentProducts) {
        validateNonNegativeUsage(currentProducts, "currentProducts");

        AccountEntitlementsSnapshot eff = resolveEffective(account);

        if (eff.unlimited()) {
            log.info(
                    "Assert de quota de produtos liberado por plano ilimitado. accountId={}, currentProducts={}",
                    account.getId(),
                    currentProducts
            );
            return;
        }

        log.info(
                "Avaliando hard limit de produtos. accountId={}, currentProducts={}, maxProducts={}",
                account.getId(),
                currentProducts,
                eff.maxProducts()
        );

        if (currentProducts >= eff.maxProducts()) {
            log.warn(
                    "Bloqueando criação de produto por hard limit. accountId={}, currentProducts={}, maxProducts={}",
                    account.getId(),
                    currentProducts,
                    eff.maxProducts()
            );

            throw new ApiException(
                    ApiErrorCode.QUOTA_MAX_PRODUCTS_REACHED,
                    "Limite de produtos atingido para este plano",
                    403
            );
        }

        log.info(
                "Assert de quota de produtos aprovado. accountId={}, currentProducts={}, maxProducts={}",
                account.getId(),
                currentProducts,
                eff.maxProducts()
        );
    }

    /**
     * Valida se a conta pode consumir armazenamento adicional.
     *
     * @param account conta alvo
     * @param currentStorageMb storage atual consumido
     * @param deltaMb incremento solicitado
     */
    public void assertCanConsumeStorage(Account account, long currentStorageMb, long deltaMb) {
        validateNonNegativeUsage(currentStorageMb, "currentStorageMb");

        if (deltaMb < 0) {
            throw new ApiException(ApiErrorCode.INVALID_STORAGE_DELTA, "deltaMb não pode ser negativo", 400);
        }

        AccountEntitlementsSnapshot eff = resolveEffective(account);

        if (eff.unlimited()) {
            log.info(
                    "Consumo de storage liberado por plano ilimitado. accountId={}, currentStorageMb={}, deltaMb={}",
                    account.getId(),
                    currentStorageMb,
                    deltaMb
            );
            return;
        }

        long after = currentStorageMb + deltaMb;

        log.info(
                "Avaliando consumo de storage. accountId={}, currentStorageMb={}, deltaMb={}, projectedStorageMb={}, maxStorageMb={}",
                account.getId(),
                currentStorageMb,
                deltaMb,
                after,
                eff.maxStorageMb()
        );

        if (after > eff.maxStorageMb()) {
            log.warn(
                    "Bloqueando consumo de storage por hard limit. accountId={}, currentStorageMb={}, deltaMb={}, projectedStorageMb={}, maxStorageMb={}",
                    account.getId(),
                    currentStorageMb,
                    deltaMb,
                    after,
                    eff.maxStorageMb()
            );

            throw new ApiException(
                    ApiErrorCode.QUOTA_MAX_STORAGE_REACHED,
                    "Limite de armazenamento atingido para este plano",
                    403
            );
        }

        log.info(
                "Assert de storage aprovado. accountId={}, projectedStorageMb={}, maxStorageMb={}",
                account.getId(),
                after,
                eff.maxStorageMb()
        );
    }

    /**
     * Resolve internamente os entitlements efetivos da conta.
     *
     * <p>Este método não deve abrir nova transação por conta própria.</p>
     *
     * @param account conta alvo
     * @return snapshot efetivo
     */
    private AccountEntitlementsSnapshot resolveEffectiveInternal(Account account) {
        if (account == null || account.getId() == null) {
            throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "Conta é obrigatória", 400);
        }

        if (account.isBuiltInAccount()) {
            log.info(
                    "Conta built-in detectada. Retornando entitlements ilimitados. accountId={}, plan={}",
                    account.getId(),
                    account.getSubscriptionPlan()
            );
            return AccountEntitlementsSnapshot.ofUnlimited();
        }

        AccountEntitlements ent = accountEntitlementsRepository.findByAccount_Id(account.getId())
                .orElse(null);

        if (ent == null) {
            log.warn(
                    "Entitlements não encontrados. Provisionando defaults. accountId={}, plan={}",
                    account.getId(),
                    account.getSubscriptionPlan()
            );

            ent = provisioningService.ensureDefaultEntitlementsForTenant(account);

            log.info(
                    "Provisionamento default de entitlements concluído. accountId={}, entitlementsFound={}",
                    account.getId(),
                    ent != null
            );
        }

        if (ent == null) {
            log.error(
                    "Falha ao resolver entitlements após provisionamento. accountId={}",
                    account.getId()
            );

            throw new ApiException(
                    ApiErrorCode.ENTITLEMENTS_UNEXPECTED_NULL,
                    "Entitlements inesperadamente nulos",
                    500
            );
        }

        Integer maxUsers = safePositive(ent.getMaxUsers(), "maxUsers", account.getId());
        Integer maxProducts = safePositive(ent.getMaxProducts(), "maxProducts", account.getId());
        Integer maxStorageMb = safePositive(ent.getMaxStorageMb(), "maxStorageMb", account.getId());

        return AccountEntitlementsSnapshot.ofLimited(maxUsers, maxProducts, maxStorageMb);
    }

    /**
     * Valida campo de entitlement limitado.
     *
     * @param value valor bruto
     * @param field nome lógico do campo
     * @param accountId id da conta para log
     * @return valor validado
     */
    private Integer safePositive(Integer value, String field, Long accountId) {
        if (value == null || value <= 0) {
            log.error(
                    "Entitlement inválido detectado. accountId={}, field={}, value={}",
                    accountId,
                    field,
                    value
            );

            throw new ApiException(
                    ApiErrorCode.INVALID_ENTITLEMENT,
                    "Entitlement inválido: " + field,
                    500
            );
        }

        return value;
    }

    /**
     * Valida se o uso informado é não negativo.
     *
     * @param usage valor de uso
     * @param field nome lógico do campo
     */
    private void validateNonNegativeUsage(long usage, String field) {
        if (usage < 0) {
            throw new ApiException(
                    ApiErrorCode.INVALID_REQUEST,
                    field + " não pode ser negativo",
                    400
            );
        }
    }
}