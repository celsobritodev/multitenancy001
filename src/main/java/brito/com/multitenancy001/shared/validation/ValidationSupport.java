package brito.com.multitenancy001.shared.validation;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.extern.slf4j.Slf4j;

/**
 * Suporte base para validações reutilizáveis da aplicação.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Centralizar o disparo de {@link ApiException} para regras de validação.</li>
 *   <li>Padronizar log de falha de validação.</li>
 *   <li>Evitar repetição de {@code if (...) throw new ApiException(...)} na app layer.</li>
 * </ul>
 *
 * <p>Importante:</p>
 * <ul>
 *   <li>Este suporte é infraestrutura de validação compartilhada.</li>
 *   <li>Services devem usar validators semânticos, não esta classe diretamente, salvo exceções justificadas.</li>
 * </ul>
 */
@Slf4j
public final class ValidationSupport {

    private ValidationSupport() {
    }

    /**
     * Garante que a condição seja verdadeira; caso contrário, lança {@link ApiException}.
     *
     * @param condition condição esperada
     * @param errorCode código do erro
     * @param message mensagem amigável para o cliente
     */
    public static void require(boolean condition, ApiErrorCode errorCode, String message) {
        require(condition, errorCode, message, 400);
    }

    /**
     * Garante que a condição seja verdadeira; caso contrário, lança {@link ApiException}.
     *
     * @param condition condição esperada
     * @param errorCode código do erro
     * @param message mensagem amigável para o cliente
     * @param status status HTTP do erro
     */
    public static void require(boolean condition, ApiErrorCode errorCode, String message, int status) {
        if (condition) {
            return;
        }

        log.warn(
                "⚠️ Falha de validação | code={} | status={} | message={}",
                errorCode.name(),
                status,
                message
        );

        throw new ApiException(errorCode, message, status);
    }
}