package brito.com.multitenancy001.controlplane.accounts.app;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.integration.tenant.TenantUsersIntegrationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço responsável pelos side effects no tenant
 * decorrentes de mudanças de lifecycle/status da account.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Suspender todos os usuários do tenant.</li>
 *   <li>Reativar todos os usuários do tenant.</li>
 *   <li>Soft delete de todos os usuários do tenant.</li>
 *   <li>Restore de todos os usuários do tenant.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountStatusTenantSideEffectService {

    private static final long TENANT_OPERATION_TIMEOUT_SECONDS = 30L;

    private final TenantUsersIntegrationService tenantUsersIntegrationService;

    /**
     * Suspende todos os usuários do tenant da conta.
     *
     * @param account conta alvo
     * @return quantidade de usuários afetados
     */
    public int suspendAllTenantUsers(Account account) {
        return tenantUsersIntegrationService.suspendAllUsersByAccount(
                account.getTenantSchema(),
                account.getId()
        );
    }

    /**
     * Remove suspensão por conta de todos os usuários do tenant.
     *
     * @param account conta alvo
     * @return quantidade de usuários afetados
     */
    public int unsuspendAllTenantUsers(Account account) {
        return tenantUsersIntegrationService.unsuspendAllUsersByAccount(
                account.getTenantSchema(),
                account.getId()
        );
    }

    /**
     * Executa soft delete de todos os usuários do tenant após soft delete da conta.
     *
     * @param account conta alvo
     */
    public void softDeleteAllTenantUsers(Account account) {
        String tenantSchema = account.getTenantSchema();
        log.info("📦 Passo 2/2: Removendo usuários do tenant [{}]", tenantSchema);

        try {
            CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() ->
                    tenantUsersIntegrationService.softDeleteAllUsersByAccount(tenantSchema, account.getId())
            );

            Integer deletedUsers = future.get(TENANT_OPERATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (deletedUsers != null && deletedUsers > 0) {
                log.info("✅ {} usuário(s) do tenant [{}] foram removidos", deletedUsers, tenantSchema);
            } else {
                log.info("ℹ️ Nenhum usuário encontrado no tenant [{}] para remover", tenantSchema);
            }

        } catch (Exception ex) {
            log.warn("⚠️ A conta [{}] foi excluída, mas houve um problema ao remover os usuários do tenant [{}].",
                    account.getId(),
                    tenantSchema);
            log.warn("   Motivo: Não foi possível completar a operação no tenant. A limpeza dos usuários precisará ser feita manualmente.");
            log.debug("Detalhes técnicos:", ex);
        }
    }

    /**
     * Executa restore de todos os usuários do tenant após restore da conta.
     *
     * @param account conta alvo
     */
    public void restoreAllTenantUsers(Account account) {
        String tenantSchema = account.getTenantSchema();
        log.info("📦 Restaurando usuários do tenant [{}]", tenantSchema);

        try {
            int restoredUsers = tenantUsersIntegrationService.restoreAllUsersByAccount(
                    tenantSchema,
                    account.getId()
            );

            log.info("✅ {} usuário(s) do tenant [{}] restaurados com sucesso", restoredUsers, tenantSchema);

        } catch (Exception ex) {
            log.warn("⚠️ A conta [{}] foi restaurada, mas houve um problema ao restaurar os usuários do tenant [{}].",
                    account.getId(),
                    tenantSchema);
            log.warn("   Motivo: {}", ex.getMessage());
            log.debug("Detalhes técnicos:", ex);
        }
    }

    /**
     * Executa soft delete de usuários do tenant durante cancelamento da conta.
     *
     * @param account conta alvo
     * @return quantidade de usuários afetados
     */
    public int softDeleteAllTenantUsersForCancellation(Account account) {
        try {
            int deletedUsers = tenantUsersIntegrationService.softDeleteAllUsersByAccount(
                    account.getTenantSchema(),
                    account.getId()
            );

            log.info("✅ {} usuário(s) do tenant removidos durante cancelamento", deletedUsers);
            return deletedUsers;

        } catch (Exception ex) {
            log.warn("⚠️ Cancelamento parcial: usuários do tenant não foram removidos. Motivo: {}", ex.getMessage());
            log.debug("Detalhes:", ex);
            return 0;
        }
    }
}