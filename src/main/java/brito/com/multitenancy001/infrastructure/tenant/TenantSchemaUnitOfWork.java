package brito.com.multitenancy001.infrastructure.tenant;

import java.util.Map;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import brito.com.multitenancy001.infrastructure.persistence.TxExecutor;
import lombok.RequiredArgsConstructor;

/**
 * Unidade de trabalho explícita para operações no Tenant Schema.
 *
 * <p><b>Responsabilidades:</b></p>
 * <ul>
 *   <li>Executar um bloco garantindo que o {@code TenantContext} esteja bindado para o schema correto.</li>
 *   <li>Centralizar fronteiras transacionais TENANT (write / read-only / requires-new).</li>
 *   <li>Evitar {@code @Transactional} espalhado e reduzir chances de wiring incorreto.</li>
 * </ul>
 *
 * <p><b>Observabilidade:</b> loga quando um tenant TX é iniciado com transação já ativa no thread,
 * o que pode indicar nesting indesejado e ajudar a diagnosticar "Pre-bound JDBC Connection found!".</p>
 */
@Component
@RequiredArgsConstructor
public class TenantSchemaUnitOfWork {

    private static final Logger log = LoggerFactory.getLogger(TenantSchemaUnitOfWork.class);

    private final TenantExecutor tenantExecutor;
    private final TxExecutor transactionExecutor;

    // REQUIRED
    public <T> T tx(String tenantSchema, Supplier<T> fn) {
        warnIfActiveTx("tx(REQUIRED)", tenantSchema);
        return tenantExecutor.runInTenantSchema(tenantSchema, () -> transactionExecutor.inTenantTx(fn));
    }

    public void tx(String tenantSchema, Runnable fn) {
        warnIfActiveTx("tx(REQUIRED)", tenantSchema);
        tenantExecutor.runInTenantSchema(tenantSchema, () -> transactionExecutor.inTenantTx(fn));
    }

    // READ ONLY
    public <T> T readOnly(String tenantSchema, Supplier<T> fn) {
        warnIfActiveTx("readOnly(REQUIRED, readOnly=true)", tenantSchema);
        return tenantExecutor.runInTenantSchema(tenantSchema, () -> transactionExecutor.inTenantReadOnlyTx(fn));
    }

    public void readOnly(String tenantSchema, Runnable fn) {
        warnIfActiveTx("readOnly(REQUIRED, readOnly=true)", tenantSchema);
        tenantExecutor.runInTenantSchema(tenantSchema, () -> transactionExecutor.inTenantReadOnlyTx(fn));
    }

    // REQUIRES_NEW
    public <T> T requiresNew(String tenantSchema, Supplier<T> fn) {
        warnIfActiveTx("requiresNew(REQUIRES_NEW)", tenantSchema);
        return tenantExecutor.runInTenantSchema(tenantSchema, () -> transactionExecutor.inTenantRequiresNew(fn));
    }

    public void requiresNew(String tenantSchema, Runnable fn) {
        warnIfActiveTx("requiresNew(REQUIRES_NEW)", tenantSchema);
        tenantExecutor.runInTenantSchema(tenantSchema, () -> transactionExecutor.inTenantRequiresNew(fn));
    }

    // REQUIRES_NEW READ ONLY
    public <T> T requiresNewReadOnly(String tenantSchema, Supplier<T> fn) {
        warnIfActiveTx("requiresNewReadOnly(REQUIRES_NEW, readOnly=true)", tenantSchema);
        return tenantExecutor.runInTenantSchema(tenantSchema, () -> transactionExecutor.inTenantRequiresNewReadOnly(fn));
    }

    public void requiresNewReadOnly(String tenantSchema, Runnable fn) {
        warnIfActiveTx("requiresNewReadOnly(REQUIRES_NEW, readOnly=true)", tenantSchema);
        tenantExecutor.runInTenantSchema(tenantSchema, () -> transactionExecutor.inTenantRequiresNewReadOnly(fn));
    }

    private static void warnIfActiveTx(String op, String tenantSchema) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) return;

        Map<Object, Object> resources = TransactionSynchronizationManager.getResourceMap();
        log.warn("⚠️ TENANT UoW chamado com transação já ativa no thread | op={} | tenantSchema={} | resources={}",
                op, tenantSchema, summarizeResources(resources));
    }

    private static String summarizeResources(Map<Object, Object> resources) {
        if (resources == null || resources.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (var e : resources.entrySet()) {
            if (!first) sb.append(", ");
            first = false;
            Object k = e.getKey();
            Object v = e.getValue();
            sb.append(k == null ? "null" : k.getClass().getName())
              .append("->")
              .append(v == null ? "null" : v.getClass().getName());
        }
        sb.append("]");
        return sb.toString();
    }
}