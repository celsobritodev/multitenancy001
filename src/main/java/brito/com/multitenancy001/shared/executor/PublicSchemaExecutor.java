package brito.com.multitenancy001.shared.executor;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import brito.com.multitenancy001.shared.context.TenantContext;


/**
 * Executor utilitário para forçar execução no schema PUBLIC (Control Plane).
 *
 * <p>Objetivo:</p>
 * <ul>
 *   <li>Garantir que operações de leitura/escrita do Control Plane ocorram no schema correto, independentemente do
 *       contexto atual do request (tenant vs public).</li>
 * </ul>
 *
 * <p>Uso recomendado:</p>
 * <ul>
 *   <li>Qualquer acesso a tabelas do Control Plane deve ser executado via {@code inPublic(...)}.</li>
 *   <li>Para operações com transação, preferir {@code PublicSchemaUnitOfWork.tx(...)}.</li>
 * </ul>
 *
 * <p>Observação:</p>
 * <ul>
 *   <li>Este executor não define transação; ele apenas aplica o escopo de schema via {@code TenantContext}.</li>
 * </ul>
 */
@Component
public class PublicSchemaExecutor {

    // ---------------------------------------------------------------------
    // ✅ PADRÃO CANÔNICO (preferido)
    // ---------------------------------------------------------------------

    /**
     * Executa a função no schema PUBLIC (Control Plane).
     * Preferir este método em todo código novo.
     */
    public <T> T inPublic(Supplier<T> supplier) {
        try (TenantContext.Scope ignored = TenantContext.publicScope()) {
            return supplier.get();
        }
    }

    /**
     * Executa o runnable no schema PUBLIC (Control Plane).
     * Preferir este método em todo código novo.
     */
    public void inPublic(Runnable runnable) {
        try (TenantContext.Scope ignored = TenantContext.publicScope()) {
            runnable.run();
        }
    }

    public TenantContext.Scope publicSchemaScope() {
        return TenantContext.publicScope();
    }

  
   
}
