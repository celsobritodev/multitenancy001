package brito.com.multitenancy001.shared.executor;

import java.util.Map;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import brito.com.multitenancy001.infrastructure.persistence.TxExecutor;
import brito.com.multitenancy001.shared.context.TenantContext;
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
 * <p><b>Regras críticas:</b></p>
 * <ul>
 *   <li>PUBLIC NÃO pode ser executado com TenantContext ativo.</li>
 *   <li>PUBLIC não deve ser iniciado dentro de TX de outro TransactionManager.</li>
 * </ul>
 *
 * <p><b>Fail-fast:</b></p>
 * <ul>
 *   <li>Qualquer violação de TenantContext ativo resulta em exception imediata.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class PublicSchemaUnitOfWork {

    private static final Logger log = LoggerFactory.getLogger(PublicSchemaUnitOfWork.class);

    private final PublicSchemaExecutor publicExecutor;
    private final TxExecutor transactionExecutor;

    // =========================================================
    // REQUIRED
    // =========================================================

    public <T> T tx(Supplier<T> fn) {
        assertNoTenantContext("tx(REQUIRED)");
        warnIfCalledInsideExistingTx("tx(REQUIRED)");
        return publicExecutor.inPublic(() -> transactionExecutor.inPublicTx(fn));
    }

    public void tx(Runnable fn) {
        assertNoTenantContext("tx(REQUIRED)");
        warnIfCalledInsideExistingTx("tx(REQUIRED)");
        publicExecutor.inPublic(() -> transactionExecutor.inPublicTx(fn));
    }

    // =========================================================
    // REQUIRES_NEW
    // =========================================================

    public <T> T requiresNew(Supplier<T> fn) {
        assertNoTenantContext("requiresNew(REQUIRES_NEW)");
        warnIfCalledInsideExistingTx("requiresNew(REQUIRES_NEW)");
        return publicExecutor.inPublic(() -> transactionExecutor.inPublicRequiresNew(fn));
    }

    public void requiresNew(Runnable fn) {
        assertNoTenantContext("requiresNew(REQUIRES_NEW)");
        warnIfCalledInsideExistingTx("requiresNew(REQUIRES_NEW)");
        publicExecutor.inPublic(() -> transactionExecutor.inPublicRequiresNew(fn));
    }

    // =========================================================
    // READ ONLY
    // =========================================================

    public <T> T readOnly(Supplier<T> fn) {
        assertNoTenantContext("readOnly(REQUIRED, readOnly=true)");
        warnIfCalledInsideExistingTx("readOnly(REQUIRED, readOnly=true)");
        return publicExecutor.inPublic(() -> transactionExecutor.inPublicReadOnlyTx(fn));
    }

    public void readOnly(Runnable fn) {
        assertNoTenantContext("readOnly(REQUIRED, readOnly=true)");
        warnIfCalledInsideExistingTx("readOnly(REQUIRED, readOnly=true)");
        publicExecutor.inPublic(() -> transactionExecutor.inPublicReadOnlyTx(fn));
    }

    // =========================================================
    // REQUIRES_NEW READ ONLY
    // =========================================================

    public <T> T requiresNewReadOnly(Supplier<T> fn) {
        assertNoTenantContext("requiresNewReadOnly(REQUIRES_NEW, readOnly=true)");
        warnIfCalledInsideExistingTx("requiresNewReadOnly(REQUIRES_NEW, readOnly=true)");
        return publicExecutor.inPublic(() -> transactionExecutor.inPublicRequiresNewReadOnly(fn));
    }

    public void requiresNewReadOnly(Runnable fn) {
        assertNoTenantContext("requiresNewReadOnly(REQUIRES_NEW, readOnly=true)");
        warnIfCalledInsideExistingTx("requiresNewReadOnly(REQUIRES_NEW, readOnly=true)");
        publicExecutor.inPublic(() -> transactionExecutor.inPublicRequiresNewReadOnly(fn));
    }

    // =========================================================
    // FAIL-FAST CRÍTICO
    // =========================================================

    /**
 /**
 * 🔒 Bloqueia execução PUBLIC com TenantContext ativo.
 *
 * <p>Regra:</p>
 * <ul>
 *   <li>PUBLIC = tenantSchema == null</li>
 *   <li>TENANT = tenantSchema != null</li>
 * </ul>
 *
 * <p>Qualquer execução PUBLIC com tenant ativo é violação crítica.</p>
 */
private void assertNoTenantContext(String op) {

    String tenantSchema = TenantContext.getOrNull();

    // PUBLIC (ok)
    if (tenantSchema == null) {
        return;
    }

    log.error(
        "❌ VIOLAÇÃO CRÍTICA: TenantContext ativo dentro de PUBLIC | op={} | tenantSchema={}",
        op,
        tenantSchema
    );

    throw new IllegalStateException(
        "TenantContext NÃO pode estar ativo dentro de PUBLIC. op=" + op +
        " | tenantSchema=" + tenantSchema
    );
}

    // =========================================================
    // WARNINGS
    // =========================================================

    private static void warnIfCalledInsideExistingTx(String op) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) return;

        Map<Object, Object> resources = TransactionSynchronizationManager.getResourceMap();

        log.warn("⚠️ PUBLIC UoW chamado com transação já ativa | op={} | resources={}",
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