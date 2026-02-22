package brito.com.multitenancy001.infrastructure.tenant;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;


/**
 * Serviço de provisionamento de schema de Tenant.
 *
 * Responsabilidades:
 * - Criar fisicamente o schema do tenant no banco de dados.
 * - Executar migrations Flyway específicas do tenant.
 * - Garantir idempotência e segurança contra provisionamento duplicado.
 *
 * Regras de arquitetura:
 * - Executa SEMPRE no contexto do Public Schema.
 * - NÃO expõe detalhes de Flyway, JDBC ou DDL para camadas superiores.
 * - NÃO contém regras de negócio de Account (apenas infraestrutura de tenant).
 *
 * Regras de negócio implícitas:
 * - Um tenant só é considerado operacional após a conclusão bem-sucedida
 *   do provisionamento do schema e das migrations.
 * - Falhas devem ser reportadas via eventos de provisioning (auditáveis).
 *
 * Observações:
 * - Este serviço é tipicamente acionado durante o onboarding de uma Account.
 * - Não deve ser chamado diretamente por controllers.
 */
@Component
@RequiredArgsConstructor
public class TenantSchemaProvisioningService {

    private final TenantSchemaProvisioningWorker tenantSchemaProvisioningWorker;

    /**
     * Account.tenantSchema é o identificador persistido do schema do tenant.
     * tenantSchema é o mesmo valor, usado como contexto de execução na infraestrutura.
     */
    public boolean ensureSchemaExistsAndMigrate(String tenantSchema) {
        return tenantSchemaProvisioningWorker.ensureSchemaExistsAndMigrate(tenantSchema);
    }

    public void tryDropSchema(String tenantSchema) {
        tenantSchemaProvisioningWorker.tryDropSchema(tenantSchema);
    }
}
