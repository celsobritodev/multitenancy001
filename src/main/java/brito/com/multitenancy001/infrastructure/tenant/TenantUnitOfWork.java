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

    public <T> T tx(String tenantSchema, Supplier<T> fn) {
        return tenantExecutor.runInTenantSchema(tenantSchema, () -> transactionExecutor.inTenantTx(fn));
    }

    public void tx(String tenantSchema, Runnable fn) {
        tenantExecutor.runInTenantSchema(tenantSchema, () -> transactionExecutor.inTenantTx(fn));
    }

    public <T> T readOnly(String tenantSchema, Supplier<T> fn) {
        return tenantExecutor.runInTenantSchema(tenantSchema, () -> transactionExecutor.inTenantReadOnlyTx(fn));
    }

    public <T> T requiresNew(String tenantSchema, Supplier<T> fn) {
        return tenantExecutor.runInTenantSchema(tenantSchema, () -> transactionExecutor.inTenantRequiresNew(fn));
    }

    public <T> T requiresNewReadOnly(String tenantSchema, Supplier<T> fn) {
        return tenantExecutor.runInTenantSchema(tenantSchema, () -> transactionExecutor.inTenantRequiresNewReadOnly(fn));
    }
}
