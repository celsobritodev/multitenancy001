// src/main/java/brito/com/multitenancy001/infrastructure/persistence/tx/AfterCommit.java
package brito.com.multitenancy001.infrastructure.persistence.tx;

import lombok.experimental.UtilityClass;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Helper para executar callbacks após COMMIT da transação corrente.
 *
 * <p>Motivação:</p>
 * <ul>
 *   <li>Evitar inconsistência: gravar algo no PUBLIC (REQUIRES_NEW) enquanto o TX do TENANT ainda pode rollbackar.</li>
 *   <li>Manter index/projeções "eventualmente consistentes", mas sem falsos positivos.</li>
 * </ul>
 *
 * <p>Uso:</p>
 * <pre>
 * AfterCommit.run(() -> publicService.ensureX(...));
 * </pre>
 */
@UtilityClass
public class AfterCommit {

    public static void run(Runnable callback) {
        if (callback == null) return;

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // Sem TX/Synchronization ativa -> executa agora.
            callback.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                callback.run();
            }
        });
    }
}