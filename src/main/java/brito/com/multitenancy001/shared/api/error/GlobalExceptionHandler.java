package brito.com.multitenancy001.shared.api.error;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;

import brito.com.multitenancy001.shared.domain.DomainException;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handler global de exceções da API.
 *
 * <p>Responsabilidades principais:</p>
 * <ul>
 *   <li>Traduzir exceções conhecidas para respostas HTTP estáveis.</li>
 *   <li>Evitar 500 em erros funcionais previsíveis.</li>
 *   <li>Padronizar payloads para consumo por frontend, integrações e suítes E2E.</li>
 *   <li>Gerar logs úteis para troubleshooting.</li>
 * </ul>
 *
 * <p>Importante para V30:</p>
 * <ul>
 *   <li>Quando uma {@link ApiException} for lançada com {@link ApiErrorCode},
 *       o campo {@code code} será exposto na raiz do JSON.</li>
 *   <li>Isso permite validar cenários como:
 *       {@code QUOTA_MAX_USERS_REACHED} e {@code QUOTA_MAX_PRODUCTS_REACHED}.</li>
 * </ul>
 */
@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

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
    @ExceptionHandler(HttpMessageNotReadableException.class)
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
     * Trata violações de integridade do banco de dados.
     *
     * <p>Mapeia constraints conhecidas para erros funcionais mais amigáveis.</p>
     *
     * @param ex exceção de integridade
     * @return response HTTP 409 padronizado
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiEnumErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        Instant ts = appNow();

        String errorMessage = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        if (!StringUtils.hasText(errorMessage)) {
            errorMessage = "";
        }

        log.warn("DataIntegrityViolationException capturada. detail={}", errorMessage);

        if (errorMessage.contains("tax_id_number")) {
            String cnpj = extractValue(errorMessage, "tax_id_number");

            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    ApiEnumErrorResponse.builder()
                            .timestamp(ts)
                            .error("DUPLICATE_NUMBER")
                            .message("Já existe uma conta com o Number: " + cnpj)
                            .field("taxIdNumber")
                            .invalidValue(cnpj)
                            .build()
            );
        }

        if (errorMessage.contains("LoginEmail")) {
            String email = extractValue(errorMessage, "LoginEmail");

            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    ApiEnumErrorResponse.builder()
                            .timestamp(ts)
                            .error("DUPLICATE_EMAIL")
                            .message("Já existe uma conta com o email " + email)
                            .field("loginEmail")
                            .invalidValue(email)
                            .build()
            );
        }

        if (errorMessage.contains("slug")) {
            String slug = extractValue(errorMessage, "slug");

            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    ApiEnumErrorResponse.builder()
                            .timestamp(ts)
                            .error("DUPLICATE_SLUG")
                            .message("Já existe uma conta com o slug " + slug)
                            .field("slug")
                            .invalidValue(slug)
                            .build()
            );
        }

        if (errorMessage.contains("tenant_schema")) {
            String schema = extractValue(errorMessage, "tenant_schema");

            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    ApiEnumErrorResponse.builder()
                            .timestamp(ts)
                            .error("DUPLICATE_SCHEMA")
                            .message("Erro interno: schema " + schema + " já existe")
                            .build()
            );
        }

        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ApiEnumErrorResponse.builder()
                        .timestamp(ts)
                        .error("DUPLICATE_ENTRY")
                        .message("Registro duplicado. Verifique os dados informados.")
                        .build()
        );
    }

    /**
     * Extrai o valor associado a uma constraint do texto bruto retornado pelo banco.
     *
     * @param message mensagem bruta da exceção
     * @param fieldName nome do campo/constraint esperado
     * @return valor extraído ou texto fallback
     */
    private String extractValue(String message, String fieldName) {
        try {
            Pattern pattern = Pattern.compile("\\(" + Pattern.quote(fieldName) + "\\)=\\(([^\\)]+)\\)");
            Matcher matcher = pattern.matcher(message);
            if (matcher.find()) {
                return matcher.group(1);
            }

            Pattern pattern2 = Pattern.compile("Key \\(" + Pattern.quote(fieldName) + "\\)=\\(([^\\)]+)\\)");
            Matcher matcher2 = pattern2.matcher(message);
            if (matcher2.find()) {
                return matcher2.group(1);
            }
        } catch (Exception e) {
            log.debug("Erro ao extrair valor do erro de constraint. field={}, message={}", fieldName, e.getMessage());
        }

        return "não identificado";
    }

    /**
     * Trata exceções padronizadas da aplicação.
     *
     * <p>Este método é o principal ajuste para a V30, pois expõe o campo
     * {@code code} na raiz da resposta.</p>
     *
     * @param ex exceção funcional da aplicação
     * @param request request HTTP atual
     * @return response HTTP com payload padronizado
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApi(ApiException ex, HttpServletRequest request) {
        Instant ts = appNow();
        String path = request != null ? request.getRequestURI() : null;
        String code = ex.getCode() != null ? ex.getCode().name() : ex.getError();
        String category = ex.getCategory() != null ? ex.getCategory().name() : null;

        log.warn(
                "ApiException capturada. status={}, code={}, category={}, path={}, message={}",
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
                .build();

        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    /**
     * Trata erros de validação de bean validation.
     *
     * @param ex exceção de validação
     * @param request request HTTP atual
     * @return response HTTP 400 padronizado
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
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
     * <p>Exemplo: GET sem {@code ?name=} quando o parâmetro é obrigatório.</p>
     *
     * @param ex exceção de parâmetro ausente
     * @return response HTTP 400 padronizado
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
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
     * <p>Exemplo: endpoint espera UUID e recebe "1".</p>
     *
     * @param ex exceção de tipo inválido
     * @return response HTTP 400 padronizado
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
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

    /**
     * Trata falhas de autenticação.
     *
     * @param ex exceção de autenticação
     * @return response HTTP 401 padronizado
     */
    @ExceptionHandler({
            BadCredentialsException.class,
            InternalAuthenticationServiceException.class,
            AuthenticationException.class
    })
    public ResponseEntity<ApiEnumErrorResponse> handleAuthentication(AuthenticationException ex) {
        Instant ts = appNow();

        log.warn("Falha de autenticação capturada. message={}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ApiEnumErrorResponse.builder()
                        .timestamp(ts)
                        .error("INVALID_USER")
                        .message("usuario ou senha invalidos")
                        .build()
        );
    }

    /**
     * Trata acesso negado no fluxo de autorização.
     *
     * @param ex exceção de autorização
     * @return response HTTP 403 padronizado
     */
    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ApiEnumErrorResponse> handleAuthorizationDenied(AuthorizationDeniedException ex) {
        Instant ts = appNow();

        log.warn("AuthorizationDeniedException capturada. message={}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                ApiEnumErrorResponse.builder()
                        .timestamp(ts)
                        .error(ApiErrorCode.FORBIDDEN.name())
                        .message("Acesso negado")
                        .build()
        );
    }

    /**
     * Trata acesso negado por segurança.
     *
     * @param ex exceção de acesso negado
     * @return response HTTP 403 padronizado
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiEnumErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        Instant ts = appNow();

        log.warn("AccessDeniedException capturada. message={}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                ApiEnumErrorResponse.builder()
                        .timestamp(ts)
                        .error(ApiErrorCode.FORBIDDEN.name())
                        .message("Acesso negado")
                        .build()
        );
    }

    /**
     * Trata violações de regra de domínio.
     *
     * @param ex exceção de domínio
     * @param request request HTTP atual
     * @return response HTTP 400 padronizado
     */
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiErrorResponse> handleDomainException(DomainException ex, HttpServletRequest request) {
        Instant ts = appNow();
        String path = request != null ? request.getRequestURI() : null;

        log.warn("DomainException capturada. path={}, message={}", path, ex.getMessage());

        ApiErrorResponse body = ApiErrorResponse.builder()
                .timestamp(ts)
                .status(HttpStatus.BAD_REQUEST.value())
                .error("DOMAIN_RULE_VIOLATION")
                .code("DOMAIN_RULE_VIOLATION")
                .category("DOMAIN")
                .message(ex.getMessage())
                .details(null)
                .path(path)
                .build();

        return ResponseEntity.badRequest().body(body);
    }

    /**
     * Trata exceções inesperadas.
     *
     * @param ex exceção não tratada
     * @return response HTTP 500 padronizado
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiEnumErrorResponse> handleGeneric(Exception ex) {
        Instant ts = appNow();

        log.error("Unhandled exception capturada. message={}", ex.getMessage(), ex);

        return ResponseEntity.internalServerError().body(
                ApiEnumErrorResponse.builder()
                        .timestamp(ts)
                        .error("INTERNAL_SERVER_ERROR")
                        .message("Erro interno inesperado. Contate o suporte.")
                        .build()
        );
    }
}