package brito.com.multitenancy001.shared.executor;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import brito.com.multitenancy001.infrastructure.persistence.TxExecutor;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PublicSchemaUnitOfWork {

    private final PublicSchemaExecutor publicExecutor;
    private final TxExecutor transactionExecutor;

    // REQUIRED
    public <T> T tx(Supplier<T> fn) {
        return publicExecutor.inPublic(() -> transactionExecutor.inPublicTx(fn));
    }

    public void tx(Runnable fn) {
        publicExecutor.inPublic(() -> transactionExecutor.inPublicTx(fn));
    }

    // REQUIRES_NEW
    public <T> T requiresNew(Supplier<T> fn) {
        return publicExecutor.inPublic(() -> transactionExecutor.inPublicRequiresNew(fn));
    }

    public void requiresNew(Runnable fn) {
        publicExecutor.inPublic(() -> transactionExecutor.inPublicRequiresNew(fn));
    }

    // READ ONLY
    public <T> T readOnly(Supplier<T> fn) {
        return publicExecutor.inPublic(() -> transactionExecutor.inPublicReadOnlyTx(fn));
    }

    public void readOnly(Runnable fn) {
        publicExecutor.inPublic(() -> transactionExecutor.inPublicReadOnlyTx(fn));
    }

    // REQUIRES_NEW READ ONLY
    public <T> T requiresNewReadOnly(Supplier<T> fn) {
        return publicExecutor.inPublic(() -> transactionExecutor.inPublicRequiresNewReadOnly(fn));
    }

    public void requiresNewReadOnly(Runnable fn) {
        publicExecutor.inPublic(() -> transactionExecutor.inPublicRequiresNewReadOnly(fn));
    }
}
