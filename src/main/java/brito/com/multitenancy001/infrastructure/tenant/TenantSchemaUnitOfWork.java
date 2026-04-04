package brito.com.multitenancy001.infrastructure.tenant;

import java.util.Map;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import brito.com.multitenancy001.infrastructure.persistence.PublicTxExecutor;
import lombok.RequiredArgsConstructor;

/**
 * Unidade de trabalho explícita para operações em tenant schema.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Executar blocos no schema do tenant correto.</li>
 *   <li>Centralizar fronteiras transacionais tenant.</li>
 *   <li>Logar nesting transacional suspeito.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class TenantSchemaUnitOfWork {

    private static final Logger log = LoggerFactory.getLogger(TenantSchemaUnitOfWork.class);

    private final TenantContextExecutor tenantExecutor;
    private final PublicTxExecutor transactionExecutor;

    public <T> T tx(String tenantSchema, Supplier<T> fn) {
        warnIfActiveTx("tx(REQUIRED)", tenantSchema);
        return tenantExecutor.runInTenantSchema(tenantSchema, () -> transactionExecutor.inTenantTx(fn));
    }

    public void tx(String tenantSchema, Runnable fn) {
        warnIfActiveTx("tx(REQUIRED)", tenantSchema);
        tenantExecutor.runInTenantSchema(tenantSchema, () -> transactionExecutor.inTenantTx(fn));
    }

    public <T> T readOnly(String tenantSchema, Supplier<T> fn) {
        warnIfActiveTx("readOnly(REQUIRED, readOnly=true)", tenantSchema);
        return tenantExecutor.runInTenantSchema(tenantSchema, () -> transactionExecutor.inTenantReadOnlyTx(fn));
    }

    public void readOnly(String tenantSchema, Runnable fn) {
        warnIfActiveTx("readOnly(REQUIRED, readOnly=true)", tenantSchema);
        tenantExecutor.runInTenantSchema(tenantSchema, () -> transactionExecutor.inTenantReadOnlyTx(fn));
    }

    public <T> T requiresNew(String tenantSchema, Supplier<T> fn) {
        warnIfActiveTx("requiresNew(REQUIRES_NEW)", tenantSchema);
        return tenantExecutor.runInTenantSchema(tenantSchema, () -> transactionExecutor.inTenantRequiresNew(fn));
    }

    public void requiresNew(String tenantSchema, Runnable fn) {
        warnIfActiveTx("requiresNew(REQUIRES_NEW)", tenantSchema);
        tenantExecutor.runInTenantSchema(tenantSchema, () -> transactionExecutor.inTenantRequiresNew(fn));
    }

    public <T> T requiresNewReadOnly(String tenantSchema, Supplier<T> fn) {
        warnIfActiveTx("requiresNewReadOnly(REQUIRES_NEW, readOnly=true)", tenantSchema);
        return tenantExecutor.runInTenantSchema(tenantSchema, () -> transactionExecutor.inTenantRequiresNewReadOnly(fn));
    }

    public void requiresNewReadOnly(String tenantSchema, Runnable fn) {
        warnIfActiveTx("requiresNewReadOnly(REQUIRES_NEW, readOnly=true)", tenantSchema);
        tenantExecutor.runInTenantSchema(tenantSchema, () -> transactionExecutor.inTenantRequiresNewReadOnly(fn));
    }

    private static void warnIfActiveTx(String op, String tenantSchema) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            return;
        }

        Map<Object, Object> resources = TransactionSynchronizationManager.getResourceMap();

        log.warn(
                "⚠️ TenantSchemaUnitOfWork iniciado com transação ativa | op={} | tenantSchema={} | resources={}",
                op,
                tenantSchema,
                resources != null ? resources.keySet() : "[]"
        );
    }
}