package brito.com.multitenancy001.shared.executor;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import brito.com.multitenancy001.shared.context.TenantContext;

@Component
public class TenantToPublicBridgeExecutor {

    public <T> T call(Supplier<T> supplier) {
        try (TenantContext.Scope ignored = TenantContext.publicScope()) {
            return supplier.get();
        }
    }

    public void run(Runnable runnable) {
        try (TenantContext.Scope ignored = TenantContext.publicScope()) {
            runnable.run();
        }
    }
}