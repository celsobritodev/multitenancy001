package brito.com.multitenancy001.infrastructure.tenant;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.context.TenantContext;
import brito.com.multitenancy001.shared.db.Schemas;
import brito.com.multitenancy001.shared.kernel.error.ApiException;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Executor central do contexto de Tenant.
 *
 * <p>Responsabilidade: garantir que o {@link TenantContext} esteja corretamente bindado
 * para um schema de tenant válido durante a execução de um bloco.</p>
 *
 * <p><b>Proteções críticas:</b></p>
 * <ul>
 *   <li>🚫 Não permite execução dentro de transação ativa (evita deadlock cross-schema).</li>
 *   <li>🚫 Não permite troca de tenant diferente no mesmo thread.</li>
 *   <li>✅ Permite reentrada idempotente quando o mesmo tenant já está ativo.</li>
 * </ul>
 */
@Component
public class TenantContextExecutor {

    private static final Logger log = LoggerFactory.getLogger(TenantContextExecutor.class);

    private final TenantSchemaProvisioner tenantSchemaProvisioningWorker;

    public TenantContextExecutor(TenantSchemaProvisioner tenantSchemaProvisioningWorker) {
        this.tenantSchemaProvisioningWorker = tenantSchemaProvisioningWorker;
    }

    // ---------------------------------------------------------------------
    // Execução: tenant pronto => sempre tenantSchema
    // ---------------------------------------------------------------------

    /**
     * Executa um bloco dentro do tenant schema informado.
     *
     * <p>Regras:</p>
     * <ul>
     *   <li>Valida o tenant informado.</li>
     *   <li>Falha se houver transação Spring ativa.</li>
     *   <li>Permite reentrada idempotente se o mesmo tenant já estiver bindado.</li>
     *   <li>Falha se houver tentativa de trocar para outro tenant no mesmo thread.</li>
     * </ul>
     *
     * @param tenantSchema schema do tenant
     * @param fn função a ser executada
     * @param <T> tipo do retorno
     * @return resultado da execução
     */
    public <T> T runInTenantSchema(String tenantSchema, Supplier<T> fn) {

        String normalizedTenantSchema = normalizeTenantSchemaOrNull(tenantSchema);

        if (normalizedTenantSchema == null || Schemas.CONTROL_PLANE.equalsIgnoreCase(normalizedTenantSchema)) {
            throw new ApiException(ApiErrorCode.TENANT_INVALID, "Tenant inválido", 404);
        }

        // 🔒 FAIL-FAST 1: impedir tenant dentro de transação
        assertNoActiveTransaction(normalizedTenantSchema);

        // 🔒 FAIL-FAST 2: impedir troca de tenant diferente, mas permitir reentrada idempotente
        if (isSameTenantAlreadyBound(normalizedTenantSchema)) {
            if (log.isDebugEnabled()) {
                log.debug("[TENANT] reentry allowed -> schema={} | motivo=mesmo tenant já ativo", normalizedTenantSchema);
            }
            return fn.get();
        }

        assertNoDifferentTenantAlreadyBound(normalizedTenantSchema);

        if (log.isDebugEnabled()) {
            log.debug("[TENANT] bind -> schema={}", normalizedTenantSchema);
        }

        try (TenantContext.Scope ignored = TenantContext.scope(normalizedTenantSchema)) {
            return fn.get();
        } finally {
            if (log.isDebugEnabled()) {
                log.debug("[TENANT] unbind -> back to PUBLIC");
            }
        }
    }

    /**
     * Executa um bloco sem retorno dentro do tenant schema informado.
     *
     * @param tenantSchema schema do tenant
     * @param fn bloco a ser executado
     */
    public void runInTenantSchema(String tenantSchema, Runnable fn) {
        runInTenantSchema(tenantSchema, () -> {
            fn.run();
            return null;
        });
    }

    // ---------------------------------------------------------------------
    // SAFE EXECUTION (IF READY)
    // ---------------------------------------------------------------------

    /**
     * Executa um bloco no tenant informado somente se schema e tabela requerida existirem.
     *
     * <p>Se o tenant não estiver pronto, retorna o valor default sem lançar erro.</p>
     *
     * @param tenantSchema schema do tenant
     * @param requiredTable tabela obrigatória para considerar o tenant pronto
     * @param fn bloco a executar
     * @param defaultValue valor default caso o tenant não esteja pronto
     * @param <T> tipo do retorno
     * @return resultado da execução ou defaultValue
     */
    public <T> T runInTenantSchemaIfReady(String tenantSchema, String requiredTable, Supplier<T> fn, T defaultValue) {
        String normalizedTenantSchema = normalizeTenantSchemaOrNull(tenantSchema);

        if (normalizedTenantSchema == null || Schemas.CONTROL_PLANE.equalsIgnoreCase(normalizedTenantSchema)) {
            if (log.isDebugEnabled()) {
                log.debug("[TENANT] skip ifReady -> tenant inválido | schema={}", tenantSchema);
            }
            return defaultValue;
        }

        if (!tenantSchemaProvisioningWorker.schemaExists(normalizedTenantSchema)) {
            if (log.isDebugEnabled()) {
                log.debug("[TENANT] skip ifReady -> schema inexistente | schema={}", normalizedTenantSchema);
            }
            return defaultValue;
        }

        if (requiredTable != null && !tenantSchemaProvisioningWorker.tableExists(normalizedTenantSchema, requiredTable)) {
            if (log.isDebugEnabled()) {
                log.debug("[TENANT] skip ifReady -> tabela ausente | schema={} | requiredTable={}",
                        normalizedTenantSchema, requiredTable);
            }
            return defaultValue;
        }

        return runInTenantSchema(normalizedTenantSchema, fn);
    }

    /**
     * Executa um bloco no tenant informado somente se schema e tabela requerida existirem.
     *
     * @param tenantSchema schema do tenant
     * @param requiredTable tabela obrigatória para considerar o tenant pronto
     * @param fn bloco a executar
     * @param <T> tipo do retorno
     * @return resultado da execução ou null
     */
    public <T> T runInTenantSchemaIfReady(String tenantSchema, String requiredTable, Supplier<T> fn) {
        return runInTenantSchemaIfReady(tenantSchema, requiredTable, fn, null);
    }

    /**
     * Executa um bloco sem retorno no tenant informado somente se schema e tabela requerida existirem.
     *
     * @param tenantSchema schema do tenant
     * @param requiredTable tabela obrigatória
     * @param fn bloco a executar
     */
    public void runInTenantSchemaIfReady(String tenantSchema, String requiredTable, Runnable fn) {
        runInTenantSchemaIfReady(tenantSchema, requiredTable, () -> {
            fn.run();
            return null;
        }, null);
    }

    // ---------------------------------------------------------------------
    // VALIDATION
    // ---------------------------------------------------------------------

    /**
     * Garante que o tenant schema e a tabela requerida existam.
     *
     * @param tenantSchema schema do tenant
     * @param requiredTable tabela obrigatória
     */
    public void assertTenantSchemaReadyOrThrow(String tenantSchema, String requiredTable) {
        String normalizedTenantSchema = normalizeTenantSchemaOrNull(tenantSchema);

        if (normalizedTenantSchema == null || Schemas.CONTROL_PLANE.equalsIgnoreCase(normalizedTenantSchema)) {
            log.error("❌ Tenant inválido na validação de readiness | tenantSchema={}", tenantSchema);
            throw new ApiException(ApiErrorCode.TENANT_INVALID, "Tenant inválido", 404);
        }

        if (!tenantSchemaProvisioningWorker.schemaExists(normalizedTenantSchema)) {
            log.error("❌ Schema do tenant não existe | tenantSchema={}", normalizedTenantSchema);
            throw new ApiException(ApiErrorCode.TENANT_SCHEMA_NOT_FOUND, "Schema do tenant não existe", 404);
        }

        if (requiredTable != null && !tenantSchemaProvisioningWorker.tableExists(normalizedTenantSchema, requiredTable)) {
            log.error(
                    "❌ Tabela obrigatória não existe no tenant | tenantSchema={} | requiredTable={}",
                    normalizedTenantSchema,
                    requiredTable
            );
            throw new ApiException(ApiErrorCode.TENANT_TABLE_NOT_FOUND, "Tabela " + requiredTable + " não existe no tenant", 404);
        }

        if (log.isDebugEnabled()) {
            log.debug(
                    "[TENANT] schema ready validated -> schema={} | requiredTable={}",
                    normalizedTenantSchema,
                    requiredTable
            );
        }
    }

    /**
     * Valida readiness do tenant e executa o bloco.
     *
     * @param tenantSchema schema do tenant
     * @param requiredTable tabela obrigatória
     * @param fn bloco a ser executado
     * @param <T> tipo do retorno
     * @return resultado da execução
     */
    public <T> T runInTenantSchemaOrThrow(String tenantSchema, String requiredTable, Supplier<T> fn) {
        assertTenantSchemaReadyOrThrow(tenantSchema, requiredTable);
        return runInTenantSchema(tenantSchema, fn);
    }

    /**
     * Valida readiness do tenant e executa o bloco.
     *
     * @param tenantSchema schema do tenant
     * @param fn bloco a ser executado
     * @param <T> tipo do retorno
     * @return resultado da execução
     */
    public <T> T runInTenantSchemaOrThrow(String tenantSchema, Supplier<T> fn) {
        return runInTenantSchemaOrThrow(tenantSchema, null, fn);
    }

    // ---------------------------------------------------------------------
    // FAIL-FAST PROTECTIONS
    // ---------------------------------------------------------------------

    /**
     * 🚫 Bloqueia execução em TENANT dentro de transação ativa.
     *
     * @param tenantSchema schema alvo
     */
    private void assertNoActiveTransaction(String tenantSchema) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            return;
        }

        log.error(
                "❌ VIOLAÇÃO CRÍTICA: tentativa de entrar em TENANT dentro de transação ativa | tenantSchema={}",
                tenantSchema
        );

        throw new IllegalStateException(
                "TenantExecutor NÃO pode ser chamado dentro de transação ativa. tenantSchema=" + tenantSchema
        );
    }

    /**
     * Verifica se já existe exatamente o mesmo tenant bindado no contexto atual.
     *
     * <p>Esse cenário é permitido como reentrada idempotente.</p>
     *
     * @param tenantSchema schema alvo
     * @return true quando o mesmo tenant já está ativo
     */
    private boolean isSameTenantAlreadyBound(String tenantSchema) {
        String current = TenantContext.getOrNull();

        if (current == null) {
            return false;
        }

        boolean sameTenant = current.equals(tenantSchema);

        if (sameTenant && log.isDebugEnabled()) {
            log.debug(
                    "[TENANT] mesmo tenant já ativo -> atual={} | novo={}",
                    current,
                    tenantSchema
            );
        }

        return sameTenant;
    }

    /**
     * 🚫 Bloqueia troca de tenant quando já existe outro tenant ativo no contexto.
     *
     * <p>Importante: este método NÃO falha para reentrada do mesmo tenant,
     * pois esse caso já foi tratado anteriormente como permitido.</p>
     *
     * @param tenantSchema schema alvo
     */
    private void assertNoDifferentTenantAlreadyBound(String tenantSchema) {

        String current = TenantContext.getOrNull();

        if (current == null) {
            return;
        }

        if (current.equals(tenantSchema)) {
            return;
        }

        log.error(
                "❌ VIOLAÇÃO CRÍTICA: Tenant já ativo no contexto com schema diferente | atual={} | novo={}",
                current,
                tenantSchema
        );

        throw new IllegalStateException(
                "Já existe TenantContext ativo com outro schema. atual=" + current + " | novo=" + tenantSchema
        );
    }

    // ---------------------------------------------------------------------
    // UTILS
    // ---------------------------------------------------------------------

    /**
     * Normaliza o tenant schema informado.
     *
     * @param tenantSchema schema bruto
     * @return schema normalizado ou null
     */
    private static String normalizeTenantSchemaOrNull(String tenantSchema) {
        String s = (tenantSchema == null ? null : tenantSchema.trim());
        return (s == null || s.isBlank()) ? null : s;
    }
}