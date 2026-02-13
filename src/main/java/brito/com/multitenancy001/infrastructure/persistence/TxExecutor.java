package brito.com.multitenancy001.infrastructure.persistence;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import brito.com.multitenancy001.infrastructure.persistence.transaction.PublicTransactionExecutor;
import brito.com.multitenancy001.infrastructure.persistence.transaction.TenantTransactionExecutor;
import lombok.RequiredArgsConstructor;

/**
 * Executor único para transações do sistema (Public schema e Tenant schema).
 *
 * - inPublicTx / inPublicReadOnlyTx
 * - inTenantTx / inTenantReadOnlyTx
 *
 * Mantém aliases "tenantTx/tenantReadOnlyTx" por compatibilidade com código legado do refactor.
 */
@Component
@RequiredArgsConstructor
public class TxExecutor {

    private final PublicTransactionExecutor publicTransactionExecutor;
    private final TenantTransactionExecutor tenantTransactionExecutor;

    // ----------------------------
    // Public schema
    // ----------------------------

    public void inPublicTx(Runnable fn) {
        publicTransactionExecutor.inPublicTx(fn);
    }

    public <T> T inPublicTx(Supplier<T> fn) {
        return publicTransactionExecutor.inPublicTx(fn);
    }

    public void inPublicReadOnlyTx(Runnable fn) {
        publicTransactionExecutor.inPublicReadOnlyTx(fn);
    }

    public <T> T inPublicReadOnlyTx(Supplier<T> fn) {
        return publicTransactionExecutor.inPublicReadOnlyTx(fn);
    }

    // ----------------------------
    // Tenant schema
    // ----------------------------

    public void inTenantTx(Runnable fn) {
        tenantTransactionExecutor.inTenantTx(fn);
    }

    public <T> T inTenantTx(Supplier<T> fn) {
        return tenantTransactionExecutor.inTenantTx(fn);
    }

    public void inTenantReadOnlyTx(Runnable fn) {
        tenantTransactionExecutor.inTenantReadOnlyTx(fn);
    }

    public <T> T inTenantReadOnlyTx(Supplier<T> fn) {
        return tenantTransactionExecutor.inTenantReadOnlyTx(fn);
    }

    // ----------------------------
    // ✅ Aliases (compatibilidade)
    // ----------------------------

    /** @deprecated use inTenantTx */
    @Deprecated
    public void tenantTx(Runnable fn) {
        inTenantTx(fn);
    }

    /** @deprecated use inTenantTx */
    @Deprecated
    public <T> T tenantTx(Supplier<T> fn) {
        return inTenantTx(fn);
    }

    /** @deprecated use inTenantReadOnlyTx */
    @Deprecated
    public void tenantReadOnlyTx(Runnable fn) {
        inTenantReadOnlyTx(fn);
    }

    /** @deprecated use inTenantReadOnlyTx */
    @Deprecated
    public <T> T tenantReadOnlyTx(Supplier<T> fn) {
        return inTenantReadOnlyTx(fn);
    }

    /** @deprecated use inPublicTx */
    @Deprecated
    public void publicTx(Runnable fn) {
        inPublicTx(fn);
    }

    /** @deprecated use inPublicTx */
    @Deprecated
    public <T> T publicTx(Supplier<T> fn) {
        return inPublicTx(fn);
    }

    /** @deprecated use inPublicReadOnlyTx */
    @Deprecated
    public void publicReadOnlyTx(Runnable fn) {
        inPublicReadOnlyTx(fn);
    }

    /** @deprecated use inPublicReadOnlyTx */
    @Deprecated
    public <T> T publicReadOnlyTx(Supplier<T> fn) {
        return inPublicReadOnlyTx(fn);
    }
}
