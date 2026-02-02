package brito.com.multitenancy001.shared.executor;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import brito.com.multitenancy001.shared.context.TenantContext;

@Component
public class PublicExecutor {

    // ---------------------------------------------------------------------
    // ✅ NOVO PADRÃO (semântico)
    // ---------------------------------------------------------------------

    public <T> T runInPublicSchema(Supplier<T> supplier) {
        return run(supplier);
    }

    public void runInPublicSchema(Runnable runnable) {
        run(runnable);
    }

    public TenantContext.Scope publicSchemaScope() {
        return TenantContext.publicScope();
    }

    // ---------------------------------------------------------------------
    // ✅ API ATUAL (mantida)
    // ---------------------------------------------------------------------

    public <T> T run(Supplier<T> supplier) {
        try (TenantContext.Scope ignored = TenantContext.publicScope()) {
            return supplier.get();
        }
    }

    public void run(Runnable runnable) {
        try (TenantContext.Scope ignored = TenantContext.publicScope()) {
            runnable.run();
        }
    }

    // aliases semânticos (opcional, mas ajuda muito leitura)
    public <T> T inPublic(Supplier<T> supplier) { return runInPublicSchema(supplier); }
    public void inPublic(Runnable runnable) { runInPublicSchema(runnable); }
}
