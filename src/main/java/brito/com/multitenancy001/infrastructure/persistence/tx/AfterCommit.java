package brito.com.multitenancy001.infrastructure.persistence.tx;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Helper para execução de callbacks relacionados ao ciclo de commit da transação corrente.
 *
 * <p>Objetivo:</p>
 * <ul>
 *   <li>Evitar side-effects PUBLIC antes da confirmação real do commit TENANT.</li>
 *   <li>Tornar explícita a semântica de execução "agora" versus "após commit".</li>
 * </ul>
 *
 * <p>Regras:</p>
 * <ul>
 *   <li>{@link #runAfterCommitRequired(Runnable)} exige sincronização transacional ativa.</li>
 *   <li>{@link #runNowOrAfterCommit(Runnable)} mantém a semântica flexível de compatibilidade.</li>
 * </ul>
 *
 * <p><b>Importante:</b></p>
 * <ul>
 *   <li>Falhas técnicas (ausência de transação ativa) são tratadas como {@link IllegalStateException}.</li>
 *   <li>Erros de entrada continuam sendo tratados via {@link ApiException}.</li>
 * </ul>
 */
@UtilityClass
@Slf4j
public class AfterCommit {

    /**
     * Executa o callback somente após commit real da transação corrente.
     *
     * <p>Se não houver sincronização transacional ativa, falha de forma explícita.</p>
     *
     * @param callback callback obrigatório
     * @throws ApiException se callback for nulo
     * @throws IllegalStateException se não houver transação ativa
     */
    public static void runAfterCommitRequired(Runnable callback) {
        if (callback == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "callback é obrigatório");
        }

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            log.error("Tentativa inválida de registrar callback after-commit sem synchronization ativa.");
            throw new IllegalStateException("Não existe transação ativa para after-commit obrigatório");
        }

        log.debug("Registrando callback obrigatório para execução após commit.");

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.debug("Executando callback obrigatório após commit.");
                callback.run();
            }
        });
    }

    /**
     * Executa o callback imediatamente se não houver TX/synchronization ativa,
     * ou registra para after-commit se houver.
     *
     * @param callback callback obrigatório
     * @throws ApiException se callback for nulo
     */
    public static void runNowOrAfterCommit(Runnable callback) {
        if (callback == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "callback é obrigatório");
        }

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            log.warn("Sem synchronization ativa. Executando callback imediatamente.");
            callback.run();
            return;
        }

        log.debug("Synchronization ativa encontrada. Registrando callback para after-commit.");

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                log.debug("Executando callback após commit.");
                callback.run();
            }
        });
    }

    /**
     * Método de compatibilidade com call-sites existentes.
     *
     * <p>Delegado para {@link #runNowOrAfterCommit(Runnable)} para manter
     * comportamento atual sem quebra.</p>
     *
     * @param callback callback obrigatório
     */
    public static void run(Runnable callback) {
        runNowOrAfterCommit(callback);
    }
}