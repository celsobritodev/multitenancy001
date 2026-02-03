package brito.com.multitenancy001.infrastructure.tenant;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import brito.com.multitenancy001.infrastructure.persistence.TransactionExecutor;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TenantUnitOfWork {

    private final TenantExecutor tenantExecutor;
    private final TransactionExecutor transactionExecutor;

    public <T> T tx(String schemaName, Supplier<T> fn) {
        return tenantExecutor.run(schemaName, () -> transactionExecutor.inTenantTx(fn));
    }
    public void tx(String schemaName, Runnable fn) {
        tenantExecutor.run(schemaName, () -> transactionExecutor.inTenantTx(fn));
    }

    public <T> T readOnly(String schemaName, Supplier<T> fn) {
        return tenantExecutor.run(schemaName, () -> transactionExecutor.inTenantReadOnlyTx(fn));
    }

    public <T> T requiresNew(String schemaName, Supplier<T> fn) {
        return tenantExecutor.run(schemaName, () -> transactionExecutor.inTenantRequiresNew(fn));
    }

    public <T> T requiresNewReadOnly(String schemaName, Supplier<T> fn) {
        return tenantExecutor.run(schemaName, () -> transactionExecutor.inTenantRequiresNewReadOnly(fn));
    }
}
