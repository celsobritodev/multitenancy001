package brito.com.multitenancy001.infrastructure.persistence;

import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class TransactionExecutor {

    private final TransactionTemplate transactionTemplatePublicTx;
    private final TransactionTemplate transactionTemplatePublicRequiresNew;

    private final TransactionTemplate transactionTemplatePublicReadOnlyTx;
    private final TransactionTemplate transactionTemplatePublicRequiresNewReadOnly;

    private final TransactionTemplate transactionTemplateTenantTx;
    private final TransactionTemplate transactionTemplateTenantRequiresNew;

    private final TransactionTemplate transactionTemplateTenantReadOnlyTx;
    private final TransactionTemplate transactionTemplateTenantRequiresNewReadOnly;

    public TransactionExecutor(
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
    public <T> T inPublicTx(Supplier<T> fn) {
        return transactionTemplatePublicTx.execute(status -> fn.get());
    }
    public void inPublicTx(Runnable fn) {
        transactionTemplatePublicTx.executeWithoutResult(status -> fn.run());
    }

    public <T> T inPublicRequiresNew(Supplier<T> fn) {
        return transactionTemplatePublicRequiresNew.execute(status -> fn.get());
    }
    public void inPublicRequiresNew(Runnable fn) {
        transactionTemplatePublicRequiresNew.executeWithoutResult(status -> fn.run());
    }

    public <T> T inPublicReadOnlyTx(Supplier<T> fn) {
        return transactionTemplatePublicReadOnlyTx.execute(status -> fn.get());
    }
    public void inPublicReadOnlyTx(Runnable fn) {
        transactionTemplatePublicReadOnlyTx.executeWithoutResult(status -> fn.run());
    }

    public <T> T inPublicRequiresNewReadOnly(Supplier<T> fn) {
        return transactionTemplatePublicRequiresNewReadOnly.execute(status -> fn.get());
    }
    public void inPublicRequiresNewReadOnly(Runnable fn) {
        transactionTemplatePublicRequiresNewReadOnly.executeWithoutResult(status -> fn.run());
    }

    // ---------- TENANT ----------
    public <T> T inTenantTx(Supplier<T> fn) {
        return transactionTemplateTenantTx.execute(status -> fn.get());
    }
    public void inTenantTx(Runnable fn) {
        transactionTemplateTenantTx.executeWithoutResult(status -> fn.run());
    }

    public <T> T inTenantRequiresNew(Supplier<T> fn) {
        return transactionTemplateTenantRequiresNew.execute(status -> fn.get());
    }
    public void inTenantRequiresNew(Runnable fn) {
        transactionTemplateTenantRequiresNew.executeWithoutResult(status -> fn.run());
    }

    public <T> T inTenantReadOnlyTx(Supplier<T> fn) {
        return transactionTemplateTenantReadOnlyTx.execute(status -> fn.get());
    }
    public void inTenantReadOnlyTx(Runnable fn) {
        transactionTemplateTenantReadOnlyTx.executeWithoutResult(status -> fn.run());
    }

    public <T> T inTenantRequiresNewReadOnly(Supplier<T> fn) {
        return transactionTemplateTenantRequiresNewReadOnly.execute(status -> fn.get());
    }
    public void inTenantRequiresNewReadOnly(Runnable fn) {
        transactionTemplateTenantRequiresNewReadOnly.executeWithoutResult(status -> fn.run());
    }
}
