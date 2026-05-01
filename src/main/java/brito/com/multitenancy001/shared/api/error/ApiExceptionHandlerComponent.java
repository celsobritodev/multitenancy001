package brito.com.multitenancy001.shared.api.error;

import java.time.Instant;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import brito.com.multitenancy001.shared.context.RequestMetaContext;
import brito.com.multitenancy001.shared.domain.DomainException;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Componente especializado em exceções funcionais e fallback genérico.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Traduzir {@link ApiException} para payload HTTP padronizado.</li>
 *   <li>Traduzir {@link DomainException} para erro funcional de domínio.</li>
 *   <li>Responder exceções inesperadas com erro 500 estável e amigável.</li>
 * </ul>
 *
 * <p>Diretrizes arquiteturais:</p>
 * <ul>
 *   <li>Registrar logs com contexto técnico e {@code requestId}.</li>
 *   <li>Não vazar detalhes internos no payload retornado ao cliente.</li>
 *   <li>Manter shape estável para frontend, integrações e E2E.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApiExceptionHandlerComponent {

    private final AppClock appClock;

    /**
     * Retorna o instante atual a partir do clock central da aplicação.
     *
     * @return instante atual da aplicação
     */
    private Instant now() {
        return appClock.instant();
    }

    /**
     * Retorna o requestId atual do contexto da requisição.
     *
     * @return requestId atual ou null
     */
    private UUID requestId() {
        return RequestMetaContext.requestIdOrNull();
    }

    /**
     * Trata exceções padronizadas da aplicação.
     *
     * @param ex exceção funcional da aplicação
     * @param request request HTTP atual
     * @return response HTTP com payload padronizado
     */
    public ResponseEntity<ApiErrorResponse> handleApi(ApiException ex, HttpServletRequest request) {
        Instant ts = now();
        String path = request != null ? request.getRequestURI() : null;
        String code = ex.getCode() != null ? ex.getCode().name() : ex.getError();
        String category = ex.getCategory() != null ? ex.getCategory().name() : null;

        log.warn(
                "⚠️ ApiException capturada | requestId={} | status={} | code={} | category={} | path={} | msg={}",
                requestId(),
                ex.getStatus(),
                code,
                category,
                path,
                ex.getMessage()
        );

        ApiErrorResponse body = ApiErrorResponse.builder()
                .timestamp(ts)
                .status(ex.getStatus())
                .error(ex.getError())
                .code(code)
                .category(category)
                .message(ex.getMessage())
                .details(ex.getDetails())
                .path(path)
                .requestId(requestId())
                .build();

        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    /**
     * Trata violações de regra de domínio.
     *
     * @param ex exceção de domínio
     * @param request request HTTP atual
     * @return response HTTP 400 padronizado
     */
    public ResponseEntity<ApiErrorResponse> handleDomainException(DomainException ex, HttpServletRequest request) {
        Instant ts = now();
        String path = request != null ? request.getRequestURI() : null;

        log.warn(
                "⚠️ DomainException capturada | requestId={} | path={} | msg={}",
                requestId(),
                path,
                ex.getMessage()
        );

        ApiErrorResponse body = ApiErrorResponse.builder()
                .timestamp(ts)
                .status(HttpStatus.BAD_REQUEST.value())
                .error("DOMAIN_RULE_VIOLATION")
                .code("DOMAIN_RULE_VIOLATION")
                .category("DOMAIN")
                .message(ex.getMessage())
                .details(null)
                .path(path)
                .requestId(requestId())
                .build();

        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Trata exceções inesperadas.
     *
     * <p>Neste fluxo o cliente recebe uma mensagem amigável e estável,
     * enquanto o detalhe técnico permanece apenas no log.</p>
     *
     * @param ex exceção não tratada
     * @return response HTTP 500 padronizado
     */
    public ResponseEntity<ApiEnumErrorResponse> handleGeneric(Exception ex) {
        Instant ts = now();

        log.error(
                "💥 Erro inesperado capturado | requestId={} | type={} | msg={}",
                requestId(),
                ex.getClass().getName(),
                ex.getMessage(),
                ex
        );

        return ResponseEntity.internalServerError().body(
                ApiEnumErrorResponse.builder()
                        .timestamp(ts)
                        .error("INTERNAL_SERVER_ERROR")
                        .message("Ocorreu um erro interno. Tente novamente ou contate o suporte.")
                        .details(new ErrorDetails(
                                requestId(),
                                "Erro interno inesperado"
                        ))
                        .build()
        );
    }
}