package brito.com.multitenancy001.shared.executor;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import brito.com.multitenancy001.shared.context.TenantContext;

@Component
public class PublicExecutor {

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
