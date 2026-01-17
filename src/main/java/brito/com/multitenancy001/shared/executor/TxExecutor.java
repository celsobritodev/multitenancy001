package brito.com.multitenancy001.shared.executor;

import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class TxExecutor {

    private final TransactionTemplate publicTx;
    private final TransactionTemplate publicRequiresNew;

    private final TransactionTemplate publicReadOnlyTx;
    private final TransactionTemplate publicRequiresNewReadOnly;

    private final TransactionTemplate tenantTx;
    private final TransactionTemplate tenantRequiresNew;

    private final TransactionTemplate tenantReadOnlyTx;
    private final TransactionTemplate tenantRequiresNewReadOnly;

    public TxExecutor(
            @Qualifier("publicTransactionManager") PlatformTransactionManager publicTm,
            @Qualifier("tenantTransactionManager") PlatformTransactionManager tenantTm
    ) {
        // PUBLIC - REQUIRED
        this.publicTx = new TransactionTemplate(publicTm);
        this.publicTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);

        // PUBLIC - REQUIRES_NEW
        this.publicRequiresNew = new TransactionTemplate(publicTm);
        this.publicRequiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // PUBLIC - REQUIRED READONLY
        this.publicReadOnlyTx = new TransactionTemplate(publicTm);
        this.publicReadOnlyTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        this.publicReadOnlyTx.setReadOnly(true);

        // PUBLIC - REQUIRES_NEW READONLY
        this.publicRequiresNewReadOnly = new TransactionTemplate(publicTm);
        this.publicRequiresNewReadOnly.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.publicRequiresNewReadOnly.setReadOnly(true);

        // TENANT - REQUIRED
        this.tenantTx = new TransactionTemplate(tenantTm);
        this.tenantTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);

        // TENANT - REQUIRES_NEW
        this.tenantRequiresNew = new TransactionTemplate(tenantTm);
        this.tenantRequiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        // TENANT - REQUIRED READONLY
        this.tenantReadOnlyTx = new TransactionTemplate(tenantTm);
        this.tenantReadOnlyTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        this.tenantReadOnlyTx.setReadOnly(true);

        // TENANT - REQUIRES_NEW READONLY
        this.tenantRequiresNewReadOnly = new TransactionTemplate(tenantTm);
        this.tenantRequiresNewReadOnly.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.tenantRequiresNewReadOnly.setReadOnly(true);
    }

    // ---------- PUBLIC ----------
    public <T> T publicTx(Supplier<T> fn) {
        return publicTx.execute(status -> fn.get());
    }
    public void publicTx(Runnable fn) {
        publicTx.executeWithoutResult(status -> fn.run());
    }

    public <T> T publicRequiresNew(Supplier<T> fn) {
        return publicRequiresNew.execute(status -> fn.get());
    }
    public void publicRequiresNew(Runnable fn) {
        publicRequiresNew.executeWithoutResult(status -> fn.run());
    }

    public <T> T publicReadOnlyTx(Supplier<T> fn) {
        return publicReadOnlyTx.execute(status -> fn.get());
    }
    public void publicReadOnlyTx(Runnable fn) {
        publicReadOnlyTx.executeWithoutResult(status -> fn.run());
    }

    public <T> T publicRequiresNewReadOnly(Supplier<T> fn) {
        return publicRequiresNewReadOnly.execute(status -> fn.get());
    }
    public void publicRequiresNewReadOnly(Runnable fn) {
        publicRequiresNewReadOnly.executeWithoutResult(status -> fn.run());
    }

    // ---------- TENANT ----------
    public <T> T tenantTx(Supplier<T> fn) {
        return tenantTx.execute(status -> fn.get());
    }
    public void tenantTx(Runnable fn) {
        tenantTx.executeWithoutResult(status -> fn.run());
    }

    public <T> T tenantRequiresNew(Supplier<T> fn) {
        return tenantRequiresNew.execute(status -> fn.get());
    }
    public void tenantRequiresNew(Runnable fn) {
        tenantRequiresNew.executeWithoutResult(status -> fn.run());
    }

    public <T> T tenantReadOnlyTx(Supplier<T> fn) {
        return tenantReadOnlyTx.execute(status -> fn.get());
    }
    public void tenantReadOnlyTx(Runnable fn) {
        tenantReadOnlyTx.executeWithoutResult(status -> fn.run());
    }

    public <T> T tenantRequiresNewReadOnly(Supplier<T> fn) {
        return tenantRequiresNewReadOnly.execute(status -> fn.get());
    }
    public void tenantRequiresNewReadOnly(Runnable fn) {
        tenantRequiresNewReadOnly.executeWithoutResult(status -> fn.run());
    }
}
