package brito.com.multitenancy001.infrastructure.persistence.transaction;

import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class PublicTransactionExecutor {

    private final PlatformTransactionManager txManager;

    public PublicTransactionExecutor(
            @Qualifier("publicTransactionManager") PlatformTransactionManager txManager
    ) {
        this.txManager = txManager;
    }

    // ----------------------------
    // REQUIRED
    // ----------------------------

    public void inPublicTx(Runnable fn) {
        inPublicTx(() -> {
            fn.run();
            return null;
        });
    }

    public <T> T inPublicTx(Supplier<T> fn) {
        return execute(TransactionDefinition.PROPAGATION_REQUIRED, false, fn);
    }

    public void inPublicReadOnlyTx(Runnable fn) {
        inPublicReadOnlyTx(() -> {
            fn.run();
            return null;
        });
    }

    public <T> T inPublicReadOnlyTx(Supplier<T> fn) {
        return execute(TransactionDefinition.PROPAGATION_REQUIRED, true, fn);
    }

    // ----------------------------
    // REQUIRES_NEW
    // ----------------------------

    public void inPublicRequiresNew(Runnable fn) {
        inPublicRequiresNew(() -> {
            fn.run();
            return null;
        });
    }

    public <T> T inPublicRequiresNew(Supplier<T> fn) {
        return execute(TransactionDefinition.PROPAGATION_REQUIRES_NEW, false, fn);
    }

    public void inPublicRequiresNewReadOnly(Runnable fn) {
        inPublicRequiresNewReadOnly(() -> {
            fn.run();
            return null;
        });
    }

    public <T> T inPublicRequiresNewReadOnly(Supplier<T> fn) {
        return execute(TransactionDefinition.PROPAGATION_REQUIRES_NEW, true, fn);
    }

    // ----------------------------
    // Internal helper
    // ----------------------------

    private <T> T execute(int propagation, boolean readOnly, Supplier<T> fn) {
        if (fn == null) throw new IllegalArgumentException("fn é obrigatório");

        TransactionTemplate tt = new TransactionTemplate(txManager);
        tt.setPropagationBehavior(propagation);
        tt.setReadOnly(readOnly);

        // TransactionTemplate#execute retorna null se callback retornar null, ok.
        return tt.execute(status -> fn.get());
    }
}
