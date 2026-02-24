package brito.com.multitenancy001.infrastructure.persistence.transaction;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Role;
import org.springframework.stereotype.Component;

import brito.com.multitenancy001.infrastructure.persistence.TxExecutor;

/**
 * Provider de execução transacional no PUBLIC schema (Control Plane).
 *
 * <p><b>Por que existe?</b></p>
 * <ul>
 *   <li>Alguns pontos do projeto preferem um provider pequeno e sem “templates por contexto”.</li>
 *   <li>Mas a regra arquitetural do projeto é: a fonte de verdade transacional é o {@link TxExecutor}.</li>
 * </ul>
 *
 * <p><b>Decisão importante:</b> este provider <b>não cria</b> {@code new TransactionTemplate(...)} por chamada.
 * Ele delega para o {@link TxExecutor}, que já possui:
 * <ul>
 *   <li>Templates pré-configurados (REQUIRED/REQUIRES_NEW + readOnly)</li>
 *   <li>Verificação de TM (JpaTransactionManager)</li>
 *   <li>Logs de diagnóstico (resources bindados, activeTx, tempo, etc.)</li>
 * </ul>
 *
 * <p>Isso evita divergência de comportamento e reduz chance de “wiring acidental” por auto-config.</p>
 */
@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
@Component
public class PublicTransactionTemplateProvider {

    private static final Logger log = LoggerFactory.getLogger(PublicTransactionTemplateProvider.class);

    private final TxExecutor txExecutor;

    public PublicTransactionTemplateProvider(TxExecutor txExecutor) {
        this.txExecutor = txExecutor;
        log.info("✅ PublicTransactionTemplateProvider inicializado (delegando para TxExecutor)");
    }

    // ----------------------------
    // REQUIRED
    // ----------------------------

    public void inPublicTx(Runnable fn) {
        if (fn == null) throw new IllegalArgumentException("fn é obrigatório");
        txExecutor.inPublicTx(fn);
    }

    public <T> T inPublicTx(Supplier<T> fn) {
        if (fn == null) throw new IllegalArgumentException("fn é obrigatório");
        return txExecutor.inPublicTx(fn);
    }

    public void inPublicReadOnlyTx(Runnable fn) {
        if (fn == null) throw new IllegalArgumentException("fn é obrigatório");
        txExecutor.inPublicReadOnlyTx(fn);
    }

    public <T> T inPublicReadOnlyTx(Supplier<T> fn) {
        if (fn == null) throw new IllegalArgumentException("fn é obrigatório");
        return txExecutor.inPublicReadOnlyTx(fn);
    }

    // ----------------------------
    // REQUIRES_NEW
    // ----------------------------

    public void inPublicRequiresNew(Runnable fn) {
        if (fn == null) throw new IllegalArgumentException("fn é obrigatório");
        txExecutor.inPublicRequiresNew(fn);
    }

    public <T> T inPublicRequiresNew(Supplier<T> fn) {
        if (fn == null) throw new IllegalArgumentException("fn é obrigatório");
        return txExecutor.inPublicRequiresNew(fn);
    }

    public void inPublicRequiresNewReadOnly(Runnable fn) {
        if (fn == null) throw new IllegalArgumentException("fn é obrigatório");
        txExecutor.inPublicRequiresNewReadOnly(fn);
    }

    public <T> T inPublicRequiresNewReadOnly(Supplier<T> fn) {
        if (fn == null) throw new IllegalArgumentException("fn é obrigatório");
        return txExecutor.inPublicRequiresNewReadOnly(fn);
    }
}