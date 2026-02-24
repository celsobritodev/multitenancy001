package brito.com.multitenancy001.infrastructure.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Role;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Executor transacional centralizado (PUBLIC e TENANT).
 *
 * <p><b>Regras do projeto:</b></p>
 * <ul>
 *   <li>PUBLIC sempre usa {@code publicTransactionManager} (JPA).</li>
 *   <li>TENANT sempre usa {@code tenantTransactionManager} (JPA multi-tenant por schema).</li>
 *   <li>TransactionManager JDBC (DataSourceTransactionManager/JdbcTransactionManager) é proibido.</li>
 * </ul>
 *
 * <p><b>Motivação:</b></p>
 * <ul>
 *   <li>Evitar o erro: "Pre-bound JDBC Connection found! ... running within DataSourceTransactionManager".</li>
 *   <li>Garantir que TransactionTemplate nunca rode em TM incorreto por auto-config.</li>
 *   <li>Centralizar logs de depuração transacional (estado + resources bindados no thread).</li>
 * </ul>
 */
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@Component
public class TxExecutor {

    private static final Logger log = LoggerFactory.getLogger(TxExecutor.class);

    private final PlatformTransactionManager publicTm;
    private final PlatformTransactionManager tenantTm;

    private final TransactionTemplate transactionTemplatePublicTx;
    private final TransactionTemplate transactionTemplatePublicRequiresNew;

    private final TransactionTemplate transactionTemplatePublicReadOnlyTx;
    private final TransactionTemplate transactionTemplatePublicRequiresNewReadOnly;

    private final TransactionTemplate transactionTemplateTenantTx;
    private final TransactionTemplate transactionTemplateTenantRequiresNew;

    private final TransactionTemplate transactionTemplateTenantReadOnlyTx;
    private final TransactionTemplate transactionTemplateTenantRequiresNewReadOnly;

    public TxExecutor(
            @Qualifier("publicTransactionManager") PlatformTransactionManager publicTm,
            @Qualifier("tenantTransactionManager") PlatformTransactionManager tenantTm
    ) {
        assertJpaTransactionManager(publicTm, "publicTransactionManager");
        assertJpaTransactionManager(tenantTm, "tenantTransactionManager");

        this.publicTm = publicTm;
        this.tenantTm = tenantTm;

        // PUBLIC - REQUIRED
        this.transactionTemplatePublicTx = new TransactionTemplate(publicTm);
        this.transactionTemplatePublicTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);

        // PUBLIC - REQUIRES_NEW
        this.transactionTemplatePublicRequiresNew = new TransactionTemplate(publicTm);
        this.transactionTemplatePublicRequiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // PUBLIC - REQUIRED READONLY
        this.transactionTemplatePublicReadOnlyTx = new TransactionTemplate(publicTm);
        this.transactionTemplatePublicReadOnlyTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        this.transactionTemplatePublicReadOnlyTx.setReadOnly(true);

        // PUBLIC - REQUIRES_NEW READONLY
        this.transactionTemplatePublicRequiresNewReadOnly = new TransactionTemplate(publicTm);
        this.transactionTemplatePublicRequiresNewReadOnly.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplatePublicRequiresNewReadOnly.setReadOnly(true);

        // TENANT - REQUIRED
        this.transactionTemplateTenantTx = new TransactionTemplate(tenantTm);
        this.transactionTemplateTenantTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);

        // TENANT - REQUIRES_NEW
        this.transactionTemplateTenantRequiresNew = new TransactionTemplate(tenantTm);
        this.transactionTemplateTenantRequiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // TENANT - REQUIRED READONLY
        this.transactionTemplateTenantReadOnlyTx = new TransactionTemplate(tenantTm);
        this.transactionTemplateTenantReadOnlyTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        this.transactionTemplateTenantReadOnlyTx.setReadOnly(true);

        // TENANT - REQUIRES_NEW READONLY
        this.transactionTemplateTenantRequiresNewReadOnly = new TransactionTemplate(tenantTm);
        this.transactionTemplateTenantRequiresNewReadOnly.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactionTemplateTenantRequiresNewReadOnly.setReadOnly(true);

        log.info("✅ TxExecutor inicializado | publicTm={} | tenantTm={}",
                describeTm(publicTm), describeTm(tenantTm));
    }

    // ---------- PUBLIC ----------
    public <T> T inPublicTx(Supplier<T> fn) {
        return execute("PUBLIC", "REQUIRED", false, () -> transactionTemplatePublicTx.execute(status -> fn.get()));
    }

    public void inPublicTx(Runnable fn) {
        execute("PUBLIC", "REQUIRED", false, () -> {
            transactionTemplatePublicTx.executeWithoutResult(status -> fn.run());
            return null;
        });
    }

    public <T> T inPublicRequiresNew(Supplier<T> fn) {
        return execute("PUBLIC", "REQUIRES_NEW", false, () -> transactionTemplatePublicRequiresNew.execute(status -> fn.get()));
    }

    public void inPublicRequiresNew(Runnable fn) {
        execute("PUBLIC", "REQUIRES_NEW", false, () -> {
            transactionTemplatePublicRequiresNew.executeWithoutResult(status -> fn.run());
            return null;
        });
    }

    public <T> T inPublicReadOnlyTx(Supplier<T> fn) {
        return execute("PUBLIC", "REQUIRED", true, () -> transactionTemplatePublicReadOnlyTx.execute(status -> fn.get()));
    }

    public void inPublicReadOnlyTx(Runnable fn) {
        execute("PUBLIC", "REQUIRED", true, () -> {
            transactionTemplatePublicReadOnlyTx.executeWithoutResult(status -> fn.run());
            return null;
        });
    }

    public <T> T inPublicRequiresNewReadOnly(Supplier<T> fn) {
        return execute("PUBLIC", "REQUIRES_NEW", true, () -> transactionTemplatePublicRequiresNewReadOnly.execute(status -> fn.get()));
    }

    public void inPublicRequiresNewReadOnly(Runnable fn) {
        execute("PUBLIC", "REQUIRES_NEW", true, () -> {
            transactionTemplatePublicRequiresNewReadOnly.executeWithoutResult(status -> fn.run());
            return null;
        });
    }

    // ---------- TENANT ----------
    public <T> T inTenantTx(Supplier<T> fn) {
        return execute("TENANT", "REQUIRED", false, () -> transactionTemplateTenantTx.execute(status -> fn.get()));
    }

    public void inTenantTx(Runnable fn) {
        execute("TENANT", "REQUIRED", false, () -> {
            transactionTemplateTenantTx.executeWithoutResult(status -> fn.run());
            return null;
        });
    }

    public <T> T inTenantRequiresNew(Supplier<T> fn) {
        return execute("TENANT", "REQUIRES_NEW", false, () -> transactionTemplateTenantRequiresNew.execute(status -> fn.get()));
    }

    public void inTenantRequiresNew(Runnable fn) {
        execute("TENANT", "REQUIRES_NEW", false, () -> {
            transactionTemplateTenantRequiresNew.executeWithoutResult(status -> fn.run());
            return null;
        });
    }

    public <T> T inTenantReadOnlyTx(Supplier<T> fn) {
        return execute("TENANT", "REQUIRED", true, () -> transactionTemplateTenantReadOnlyTx.execute(status -> fn.get()));
    }

    public void inTenantReadOnlyTx(Runnable fn) {
        execute("TENANT", "REQUIRED", true, () -> {
            transactionTemplateTenantReadOnlyTx.executeWithoutResult(status -> fn.run());
            return null;
        });
    }

    public <T> T inTenantRequiresNewReadOnly(Supplier<T> fn) {
        return execute("TENANT", "REQUIRES_NEW", true, () -> transactionTemplateTenantRequiresNewReadOnly.execute(status -> fn.get()));
    }

    public void inTenantRequiresNewReadOnly(Runnable fn) {
        execute("TENANT", "REQUIRES_NEW", true, () -> {
            transactionTemplateTenantRequiresNewReadOnly.executeWithoutResult(status -> fn.run());
            return null;
        });
    }

    // ---------------------------------------------------------------------
    // Internals: logging / diagnostics
    // ---------------------------------------------------------------------

    private <T> T execute(String scope, String propagation, boolean readOnly, Supplier<T> supplier) {
        boolean activeBefore = TransactionSynchronizationManager.isActualTransactionActive();
        boolean syncBefore = TransactionSynchronizationManager.isSynchronizationActive();
        Map<Object, Object> resourcesBefore = TransactionSynchronizationManager.getResourceMap();

        long start = System.currentTimeMillis();

        if (log.isDebugEnabled()) {
            log.debug("▶ TX BEGIN (before) | scope={} | propagation={} | readOnly={} | activeTx={} | sync={} | resources={}",
                    scope, propagation, readOnly, activeBefore, syncBefore, summarizeResources(resourcesBefore));
        }

        try {
            T out = supplier.get();

            long ms = System.currentTimeMillis() - start;
            if (log.isDebugEnabled()) {
                log.debug("✅ TX END (after) | scope={} | propagation={} | readOnly={} | tookMs={} | activeTxNow={} | syncNow={} | resourcesNow={}",
                        scope, propagation, readOnly, ms,
                        TransactionSynchronizationManager.isActualTransactionActive(),
                        TransactionSynchronizationManager.isSynchronizationActive(),
                        summarizeResources(TransactionSynchronizationManager.getResourceMap()));
            }
            return out;
        } catch (RuntimeException ex) {
            long ms = System.currentTimeMillis() - start;

            log.error("❌ TX ERROR | scope={} | propagation={} | readOnly={} | tookMs={} | activeTxBefore={} | syncBefore={} | resourcesBefore={} | tmPublic={} | tmTenant={} | exType={} | msg={}",
                    scope, propagation, readOnly, ms,
                    activeBefore, syncBefore, summarizeResources(resourcesBefore),
                    describeTm(publicTm), describeTm(tenantTm),
                    ex.getClass().getSimpleName(), safeMsg(ex), ex);

            throw ex;
        }
    }

    /**
     * Loga um resumo compacto dos resources bindados no thread.
     *
     * <p>Importante para detectar "Pre-bound JDBC Connection": normalmente aparece um resource
     * relacionado a DataSource/ConnectionHolder/JdbcTemplate etc.</p>
     */
    private static String summarizeResources(Map<Object, Object> resources) {
        if (resources == null || resources.isEmpty()) return "[]";

        List<String> out = new ArrayList<>();
        for (var e : resources.entrySet()) {
            Object k = e.getKey();
            Object v = e.getValue();
            String ks = (k == null ? "null" : k.getClass().getName());
            String vs = (v == null ? "null" : v.getClass().getName());
            out.add(ks + "->" + vs);
        }
        return out.toString();
    }

    private static String describeTm(PlatformTransactionManager tm) {
        Class<?> target = AopUtils.getTargetClass(tm);
        if (target == null) target = tm.getClass();
        return target.getName();
    }

    private static String safeMsg(Throwable t) {
        String m = t.getMessage();
        return (m == null ? "" : m.replace("\n", " ").replace("\r", " "));
    }

    private static void assertJpaTransactionManager(PlatformTransactionManager tm, String expectedBeanName) {
        Class<?> target = AopUtils.getTargetClass(tm);
        if (target == null) target = tm.getClass();

        if (!JpaTransactionManager.class.isAssignableFrom(target)) {
            String msg = "\n\n🚫 TransactionManager inválido injetado em TxExecutor.\n"
                    + "Bean esperado: '" + expectedBeanName + "' (JpaTransactionManager)\n"
                    + "Tipo real: " + target.getName() + "\n\n"
                    + "Isso causa: 'Pre-bound JDBC Connection found!...'\n"
                    + "Correção: garanta que '" + expectedBeanName + "' seja JpaTransactionManager.\n";
            throw new IllegalStateException(msg);
        }
    }
}