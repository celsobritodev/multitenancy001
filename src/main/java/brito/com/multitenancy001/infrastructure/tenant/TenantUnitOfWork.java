package brito.com.multitenancy001.infrastructure.tenant;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import brito.com.multitenancy001.shared.executor.TxExecutor;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TenantUnitOfWork {

    private final TenantExecutor tenantExecutor;
    private final TxExecutor txExecutor;

    public <T> T tx(String schemaName, Supplier<T> fn) {
        return tenantExecutor.run(schemaName, () -> txExecutor.tenantTx(fn));
    }
    public void tx(String schemaName, Runnable fn) {
        tenantExecutor.run(schemaName, () -> txExecutor.tenantTx(fn));
    }

    public <T> T readOnly(String schemaName, Supplier<T> fn) {
        return tenantExecutor.run(schemaName, () -> txExecutor.tenantReadOnlyTx(fn));
    }

    public <T> T requiresNew(String schemaName, Supplier<T> fn) {
        return tenantExecutor.run(schemaName, () -> txExecutor.tenantRequiresNew(fn));
    }

    public <T> T requiresNewReadOnly(String schemaName, Supplier<T> fn) {
        return tenantExecutor.run(schemaName, () -> txExecutor.tenantRequiresNewReadOnly(fn));
    }
}
