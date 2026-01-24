package brito.com.multitenancy001.shared.executor;

import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class TxExecutor {

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
    }

    // ---------- PUBLIC ----------
    public <T> T publicTx(Supplier<T> fn) {
        return transactionTemplatePublicTx.execute(status -> fn.get());
    }
    public void publicTx(Runnable fn) {
        transactionTemplatePublicTx.executeWithoutResult(status -> fn.run());
    }

    public <T> T publicRequiresNew(Supplier<T> fn) {
        return transactionTemplatePublicRequiresNew.execute(status -> fn.get());
    }
    public void publicRequiresNew(Runnable fn) {
        transactionTemplatePublicRequiresNew.executeWithoutResult(status -> fn.run());
    }

    public <T> T publicReadOnlyTx(Supplier<T> fn) {
        return transactionTemplatePublicReadOnlyTx.execute(status -> fn.get());
    }
    public void publicReadOnlyTx(Runnable fn) {
        transactionTemplatePublicReadOnlyTx.executeWithoutResult(status -> fn.run());
    }

    public <T> T publicRequiresNewReadOnly(Supplier<T> fn) {
        return transactionTemplatePublicRequiresNewReadOnly.execute(status -> fn.get());
    }
    public void publicRequiresNewReadOnly(Runnable fn) {
        transactionTemplatePublicRequiresNewReadOnly.executeWithoutResult(status -> fn.run());
    }

    // ---------- TENANT ----------
    public <T> T tenantTx(Supplier<T> fn) {
        return transactionTemplateTenantTx.execute(status -> fn.get());
    }
    public void tenantTx(Runnable fn) {
        transactionTemplateTenantTx.executeWithoutResult(status -> fn.run());
    }

    public <T> T tenantRequiresNew(Supplier<T> fn) {
        return transactionTemplateTenantRequiresNew.execute(status -> fn.get());
    }
    public void tenantRequiresNew(Runnable fn) {
        transactionTemplateTenantRequiresNew.executeWithoutResult(status -> fn.run());
    }

    public <T> T tenantReadOnlyTx(Supplier<T> fn) {
        return transactionTemplateTenantReadOnlyTx.execute(status -> fn.get());
    }
    public void tenantReadOnlyTx(Runnable fn) {
        transactionTemplateTenantReadOnlyTx.executeWithoutResult(status -> fn.run());
    }

    public <T> T tenantRequiresNewReadOnly(Supplier<T> fn) {
        return transactionTemplateTenantRequiresNewReadOnly.execute(status -> fn.get());
    }
    public void tenantRequiresNewReadOnly(Runnable fn) {
        transactionTemplateTenantRequiresNewReadOnly.executeWithoutResult(status -> fn.run());
    }
}
