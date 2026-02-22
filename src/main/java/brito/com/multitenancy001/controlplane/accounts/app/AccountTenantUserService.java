package brito.com.multitenancy001.controlplane.accounts.app;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;

import java.util.List;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.accounts.domain.Account;
import brito.com.multitenancy001.controlplane.accounts.persistence.AccountRepository;
import brito.com.multitenancy001.shared.contracts.TenantUserOperations;
import brito.com.multitenancy001.shared.contracts.UserSummaryData;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Serviço de aplicação responsável por operações administrativas
 * sobre usuários de tenants no contexto do Control Plane.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Suspender ou reativar usuários por decisão administrativa.</li>
 *   <li>Garantir consistência entre Account e seus Tenant Users.</li>
 *   <li>Atuar como fachada para operações em massa em usuários de tenants.</li>
 * </ul>
 *
 * <p>Regras de Negócio:</p>
 * <ul>
 *   <li>Usuários suspensos por admin não podem se autenticar.</li>
 *   <li>Operações respeitam o estado operacional da Account.</li>
 *   <li>O último TENANT_OWNER ativo de uma conta não pode ser suspenso ou deletado.</li>
 * </ul>
 *
 * <p>Arquitetura:</p>
 * <ul>
 *   <li>Este serviço NÃO conhece os detalhes de implementação do Tenant.
 *       Ele depende da abstração {@link TenantUserOperations}, que é implementada
 *       pelo módulo de integração.</li>
 *   <li>Isolamento completo entre os bounded contexts ControlPlane e Tenant.</li>
 * </ul>
 *
 * @see TenantUserOperations
 * @see brito.com.multitenancy001.integration.tenant.TenantProvisioningIntegrationService
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountTenantUserService {

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final AccountRepository accountRepository;
    
    // ✅ Agora depende da INTERFACE, não da implementação concreta
    private final TenantUserOperations tenantUserOperations;

    /**
     * Lista os usuários de um tenant, com opção de filtrar apenas os operacionais.
     *
     * @param accountId       O ID da conta (obrigatório).
     * @param onlyOperational Se true, retorna apenas usuários operacionais.
     * @return Lista de resumos dos usuários.
     * @throws ApiException Se a conta não for encontrada.
     */
    public List<UserSummaryData> listTenantUsers(Long accountId, boolean onlyOperational) {
        Account account = publicSchemaUnitOfWork.readOnly(() ->
                accountRepository.findByIdAndDeletedFalse(accountId)
                        .orElseThrow(() -> new ApiException(
                                ApiErrorCode.ACCOUNT_NOT_FOUND, 
                                "Conta não encontrada", 
                                404))
        );

        String tenantSchema = account.getTenantSchema();

        return tenantUserOperations.listUserSummaries(tenantSchema, account.getId(), onlyOperational);
    }

    /**
     * Define o status de suspensão por admin de um usuário específico.
     *
     * @param accountId O ID da conta (obrigatório).
     * @param userId    O ID do usuário alvo (obrigatório).
     * @param suspended O novo status de suspensão.
     * @throws ApiException Se a conta não for encontrada ou a operação violar regras.
     */
    public void setUserSuspendedByAdmin(Long accountId, Long userId, boolean suspended) {
        Account account = publicSchemaUnitOfWork.readOnly(() ->
                accountRepository.findByIdAndDeletedFalse(accountId)
                        .orElseThrow(() -> new ApiException(
                                ApiErrorCode.ACCOUNT_NOT_FOUND, 
                                "Conta não encontrada", 
                                404))
        );

        String tenantSchema = account.getTenantSchema();

        tenantUserOperations.setUserSuspendedByAdmin(tenantSchema, account.getId(), userId, suspended);
    }
}