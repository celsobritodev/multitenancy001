package brito.com.multitenancy001.shared.api.error;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;

import brito.com.multitenancy001.shared.time.AppClock;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Support especializado em erros de validação, parsing e parâmetros HTTP.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Traduzir body inválido e enum inválido.</li>
 *   <li>Traduzir erros de bean validation.</li>
 *   <li>Traduzir ausência de request parameter.</li>
 *   <li>Traduzir type mismatch em path variable e query param.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ValidationExceptionHandlerSupport {

    private final AppClock appClock;

    /**
     * Retorna o instante atual a partir do clock central da aplicação.
     *
     * @return instante atual da aplicação
     */
    private Instant appNow() {
        return appClock.instant();
    }

    /**
     * Trata falhas de parsing do corpo da requisição.
     *
     * <p>Se a falha vier de enum inválido, devolve payload rico com valores permitidos.
     * Caso contrário, devolve erro genérico de body inválido.</p>
     *
     * @param ex exceção lançada pelo parser HTTP/Jackson
     * @return response HTTP 400 padronizado
     */
    public ResponseEntity<ApiEnumErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        Instant ts = appNow();
        Throwable cause = ex.getCause();

        log.warn("HttpMessageNotReadableException capturada. message={}", ex.getMessage());

        if (cause instanceof InvalidFormatException ife) {
            Class<?> targetType = ife.getTargetType();

            if (targetType != null && targetType.isEnum()) {
                String fieldName = ife.getPath().isEmpty() ? "status" : ife.getPath().get(0).getFieldName();
                String invalidValue = ife.getValue() != null ? ife.getValue().toString() : "null";

                List<String> allowedValues = Arrays.stream(targetType.getEnumConstants())
                        .map(Object::toString)
                        .toList();

                log.warn(
                        "Enum inválido recebido. field={}, invalidValue={}, allowedValues={}",
                        fieldName,
                        invalidValue,
                        allowedValues
                );

                return ResponseEntity.badRequest().body(
                        ApiEnumErrorResponse.builder()
                                .timestamp(ts)
                                .error("INVALID_ENUM")
                                .message("Valor inválido para o campo " + fieldName)
                                .field(fieldName)
                                .invalidValue(invalidValue)
                                .allowedValues(allowedValues)
                                .build()
                );
            }
        }

        return ResponseEntity.badRequest().body(
                ApiEnumErrorResponse.builder()
                        .timestamp(ts)
                        .error("INVALID_REQUEST_BODY")
                        .message("Corpo da requisição inválido")
                        .build()
        );
    }

    /**
     * Trata erros de validação de bean validation.
     *
     * @param ex exceção de validação
     * @param request request HTTP atual
     * @return response HTTP 400 padronizado
     */
    public ResponseEntity<ApiErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        Instant ts = appNow();
        String path = request != null ? request.getRequestURI() : null;

        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();

        log.warn("Erro de validação capturado. path={}, errors={}", path, errors);

        ApiErrorResponse errorResponse = ApiErrorResponse.builder()
                .timestamp(ts)
                .status(HttpStatus.BAD_REQUEST.value())
                .error("VALIDATION_ERROR")
                .code("VALIDATION_ERROR")
                .category("VALIDATION")
                .message("Erro de validação")
                .details(errors)
                .path(path)
                .build();

        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * Trata ausência de parâmetro obrigatório de request.
     *
     * @param ex exceção de parâmetro ausente
     * @return response HTTP 400 padronizado
     */
    public ResponseEntity<ApiEnumErrorResponse> handleMissingRequestParam(MissingServletRequestParameterException ex) {
        Instant ts = appNow();
        String param = ex.getParameterName();

        log.warn("Missing request parameter capturado. param={}", param);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ApiEnumErrorResponse.builder()
                        .timestamp(ts)
                        .error("MISSING_REQUEST_PARAMETER")
                        .message("Parâmetro obrigatório ausente: " + param)
                        .field(param)
                        .invalidValue(null)
                        .allowedValues(null)
                        .details(null)
                        .build()
        );
    }

    /**
     * Trata mismatch de tipo em path variable ou request param.
     *
     * @param ex exceção de tipo inválido
     * @return response HTTP 400 padronizado
     */
    public ResponseEntity<ApiEnumErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        Instant ts = appNow();

        String field = ex.getName();
        Object value = ex.getValue();
        String invalidValue = value == null ? "null" : value.toString();
        String expectedType = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown";

        log.warn(
                "Type mismatch capturado. field={}, invalidValue={}, expectedType={}",
                field,
                invalidValue,
                expectedType
        );

        return ResponseEntity.badRequest().body(
                ApiEnumErrorResponse.builder()
                        .timestamp(ts)
                        .error("INVALID_PARAMETER")
                        .message("Parâmetro inválido: " + field)
                        .field(field)
                        .invalidValue(invalidValue)
                        .allowedValues(List.of(expectedType))
                        .details(null)
                        .build()
        );
    }
}