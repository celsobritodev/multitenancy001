package brito.com.multitenancy001.shared.executor;

import java.util.Map;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import brito.com.multitenancy001.infrastructure.persistence.TxExecutor;
import lombok.RequiredArgsConstructor;

/**
 * Unidade de trabalho explícita para operações no Public Schema (Control Plane).
 *
 * <p><b>Responsabilidades:</b></p>
 * <ul>
 *   <li>Controlar fronteiras transacionais no PUBLIC.</li>
 *   <li>Diferenciar operações read-only vs comandos (write).</li>
 *   <li>Evitar vazamento de {@code @Transactional} espalhado na aplicação.</li>
 * </ul>
 *
 * <p><b>Regra crítica:</b></p>
 * <ul>
 *   <li>PUBLIC não deve ser iniciado dentro de um TX de outro TransactionManager.
 *       Isso tende a causar: "Pre-bound JDBC Connection found!".</li>
 * </ul>
 *
 * <p><b>Observabilidade:</b> adiciona logs quando é chamado com transação já ativa no thread.</p>
 */
@Component
@RequiredArgsConstructor
public class PublicSchemaUnitOfWork {

    private static final Logger log = LoggerFactory.getLogger(PublicSchemaUnitOfWork.class);

    private final PublicSchemaExecutor publicExecutor;
    private final TxExecutor transactionExecutor;

    // REQUIRED
    public <T> T tx(Supplier<T> fn) {
        warnIfCalledInsideExistingTx("tx(REQUIRED)");
        return publicExecutor.inPublic(() -> transactionExecutor.inPublicTx(fn));
    }

    public void tx(Runnable fn) {
        warnIfCalledInsideExistingTx("tx(REQUIRED)");
        publicExecutor.inPublic(() -> transactionExecutor.inPublicTx(fn));
    }

    // REQUIRES_NEW
    public <T> T requiresNew(Supplier<T> fn) {
        warnIfCalledInsideExistingTx("requiresNew(REQUIRES_NEW)");
        return publicExecutor.inPublic(() -> transactionExecutor.inPublicRequiresNew(fn));
    }

    public void requiresNew(Runnable fn) {
        warnIfCalledInsideExistingTx("requiresNew(REQUIRES_NEW)");
        publicExecutor.inPublic(() -> transactionExecutor.inPublicRequiresNew(fn));
    }

    // READ ONLY
    public <T> T readOnly(Supplier<T> fn) {
        warnIfCalledInsideExistingTx("readOnly(REQUIRED, readOnly=true)");
        return publicExecutor.inPublic(() -> transactionExecutor.inPublicReadOnlyTx(fn));
    }

    public void readOnly(Runnable fn) {
        warnIfCalledInsideExistingTx("readOnly(REQUIRED, readOnly=true)");
        publicExecutor.inPublic(() -> transactionExecutor.inPublicReadOnlyTx(fn));
    }

    // REQUIRES_NEW READ ONLY
    public <T> T requiresNewReadOnly(Supplier<T> fn) {
        warnIfCalledInsideExistingTx("requiresNewReadOnly(REQUIRES_NEW, readOnly=true)");
        return publicExecutor.inPublic(() -> transactionExecutor.inPublicRequiresNewReadOnly(fn));
    }

    public void requiresNewReadOnly(Runnable fn) {
        warnIfCalledInsideExistingTx("requiresNewReadOnly(REQUIRES_NEW, readOnly=true)");
        publicExecutor.inPublic(() -> transactionExecutor.inPublicRequiresNewReadOnly(fn));
    }

    private static void warnIfCalledInsideExistingTx(String op) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) return;

        Map<Object, Object> resources = TransactionSynchronizationManager.getResourceMap();
        log.warn("⚠️ PUBLIC UoW chamado com transação já ativa no thread | op={} | resources={}",
                op, summarizeResources(resources));
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