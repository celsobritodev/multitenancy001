package brito.com.multitenancy001.shared.executor;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PublicUnitOfWork {

    private final PublicExecutor publicExecutor;
    private final TxExecutor txExecutor;

    // REQUIRED
    public <T> T tx(Supplier<T> fn) {
        return publicExecutor.run(() -> txExecutor.publicTx(fn));
    }

    public void tx(Runnable fn) {
        publicExecutor.run(() -> txExecutor.publicTx(fn));
    }

    // REQUIRES_NEW
    public <T> T requiresNew(Supplier<T> fn) {
        return publicExecutor.run(() -> txExecutor.publicRequiresNew(fn));
    }

    public void requiresNew(Runnable fn) {
        publicExecutor.run(() -> txExecutor.publicRequiresNew(fn));
    }

    // READ ONLY
    public <T> T readOnly(Supplier<T> fn) {
        return publicExecutor.run(() -> txExecutor.publicReadOnlyTx(fn));
    }

    public void readOnly(Runnable fn) {
        publicExecutor.run(() -> txExecutor.publicReadOnlyTx(fn));
    }

    // REQUIRES_NEW READ ONLY
    public <T> T requiresNewReadOnly(Supplier<T> fn) {
        return publicExecutor.run(() -> txExecutor.publicRequiresNewReadOnly(fn));
    }

    public void requiresNewReadOnly(Runnable fn) {
        publicExecutor.run(() -> txExecutor.publicRequiresNewReadOnly(fn));
    }
}
