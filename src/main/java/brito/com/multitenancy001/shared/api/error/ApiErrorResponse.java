package brito.com.multitenancy001.shared.api.error;

import java.time.Instant;
import java.util.UUID;

import lombok.Builder;

/**
 * Payload padronizado de erro da API.
 *
 * <p>Responsável por representar erros retornados ao cliente
 * de forma estável, previsível e rastreável.</p>
 *
 * <p>Campos:</p>
 * <ul>
 *   <li><b>timestamp</b>: instante do erro (AppClock).</li>
 *   <li><b>status</b>: status HTTP numérico.</li>
 *   <li><b>error</b>: código externo do erro (ex: "INVALID_REQUEST").</li>
 *   <li><b>code</b>: código interno da aplicação (ex: ApiErrorCode).</li>
 *   <li><b>category</b>: categoria funcional do erro (ex: AUTH, VALIDATION, DOMAIN).</li>
 *   <li><b>message</b>: mensagem amigável para o cliente.</li>
 *   <li><b>details</b>: dados adicionais (opcional, estruturado ou lista).</li>
 *   <li><b>path</b>: endpoint que gerou o erro.</li>
 *   <li><b>requestId</b>: identificador único da requisição (rastreamento).</li>
 * </ul>
 *
 * <p>Diretrizes de uso:</p>
 * <ul>
 *   <li>NUNCA expor stacktrace ou mensagens técnicas no campo {@code message}.</li>
 *   <li>{@code details} pode conter:
 *       <ul>
 *           <li>lista de erros de validação</li>
 *           <li>{@link ErrorDetails} para rastreamento</li>
 *           <li>dados auxiliares controlados</li>
 *       </ul>
 *   </li>
 *   <li>{@code requestId} deve sempre ser preenchido quando disponível.</li>
 * </ul>
 *
 * <p>Compatibilidade:</p>
 * <ul>
 *   <li>Consumido por {@link GlobalExceptionHandler}.</li>
 *   <li>Preenchido por {@link ApiExceptionHandlerComponent}.</li>
 *   <li>Usado em E2E e integrações externas.</li>
 * </ul>
 */
@Builder
public record ApiErrorResponse(

        Instant timestamp,

        Integer status,

        /**
         * Código externo do erro (consumido por frontend/integradores).
         */
        String error,

        /**
         * Código interno da aplicação (normalmente ApiErrorCode.name()).
         */
        String code,

        /**
         * Categoria funcional do erro.
         */
        String category,

        /**
         * Mensagem amigável ao usuário.
         */
        String message,

        /**
         * Detalhes adicionais do erro (opcional).
         */
        Object details,

        /**
         * Caminho da requisição HTTP.
         */
        String path,

        /**
         * Identificador único da requisição para rastreamento.
         */
        UUID requestId

) {
}