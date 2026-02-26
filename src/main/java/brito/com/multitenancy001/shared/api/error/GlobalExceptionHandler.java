package brito.com.multitenancy001.shared.api.error;

import brito.com.multitenancy001.shared.domain.DomainException;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

    private final AppClock appClock;

    private Instant appNow() {
        return appClock.instant();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiEnumErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        Instant ts = appNow();

        Throwable cause = ex.getCause();

        if (cause instanceof InvalidFormatException ife) {
            Class<?> targetType = ife.getTargetType();

            if (targetType != null && targetType.isEnum()) {
                String fieldName = ife.getPath().isEmpty() ? "status" : ife.getPath().get(0).getFieldName();
                String invalidValue = ife.getValue() != null ? ife.getValue().toString() : "null";

                List<String> allowedValues = Arrays.stream(targetType.getEnumConstants())
                        .map(Object::toString)
                        .toList();

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

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiEnumErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        Instant ts = appNow();

        String errorMessage = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        if (!StringUtils.hasText(errorMessage)) errorMessage = "";

        log.debug("DataIntegrityViolationException: {}", errorMessage);

        if (errorMessage.contains("tax_id_number")) {
            String cnpj = extractValue(errorMessage, "tax_id_number");
            return ResponseEntity.status(409).body(
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
            return ResponseEntity.status(409).body(
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
            return ResponseEntity.status(409).body(
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
            return ResponseEntity.status(409).body(
                    ApiEnumErrorResponse.builder()
                            .timestamp(ts)
                            .error("DUPLICATE_SCHEMA")
                            .message("Erro interno: schema " + schema + " já existe")
                            .build()
            );
        }

        return ResponseEntity.status(409).body(
                ApiEnumErrorResponse.builder()
                        .timestamp(ts)
                        .error("DUPLICATE_ENTRY")
                        .message("Registro duplicado. Verifique os dados informados.")
                        .build()
        );
    }

    private String extractValue(String message, String fieldName) {
        try {
            Pattern pattern = Pattern.compile("\\(" + Pattern.quote(fieldName) + "\\)=\\(([^\\)]+)\\)");
            Matcher matcher = pattern.matcher(message);
            if (matcher.find()) return matcher.group(1);

            Pattern pattern2 = Pattern.compile("Key \\(" + Pattern.quote(fieldName) + "\\)=\\(([^\\)]+)\\)");
            Matcher matcher2 = pattern2.matcher(message);
            if (matcher2.find()) return matcher2.group(1);

        } catch (Exception e) {
            log.debug("Erro ao extrair valor do erro de constraint: {}", e.getMessage());
        }

        return "não identificado";
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiEnumErrorResponse> handleApi(ApiException ex) {
        Instant ts = appNow();

        return ResponseEntity.status(ex.getStatus()).body(
                ApiEnumErrorResponse.builder()
                        .timestamp(ts)
                        .error(ex.getError())
                        .message(ex.getMessage())
                        .field(ex.getField())
                        .invalidValue(ex.getInvalidValue())
                        .allowedValues(ex.getAllowedValues())
                        .details(ex.getDetails())
                        .build()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Instant ts = appNow();

        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();

        ApiErrorResponse errorResponse = ApiErrorResponse.builder()
                .timestamp(ts)
                .error("VALIDATION_ERROR")
                .message("Erro de validação")
                .details(errors)
                .build();

        return ResponseEntity.badRequest().body(errorResponse);
    }

    /**
     * ✅ Se faltar @RequestParam obrigatório, é 400 (request inválida), não 500.
     * Ex.: GET /api/tenant/categories/search sem ?name=...
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiEnumErrorResponse> handleMissingRequestParam(MissingServletRequestParameterException ex) {
        Instant ts = appNow();

        String param = ex.getParameterName();
        log.warn("Missing request parameter: {}", param);

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
     * ✅ NOVO: quando o path/query param vem com tipo inválido (ex.: UUID esperado e recebeu "1"),
     * isso deve ser 400 e não 500.
     *
     * Ex.: GET /api/tenant/products/1 quando o controller espera UUID.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiEnumErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        Instant ts = appNow();

        String field = ex.getName(); // nome do path var / param
        Object value = ex.getValue();
        String invalidValue = value == null ? "null" : value.toString();

        String expectedType = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "unknown";

        log.warn("Type mismatch for '{}': value='{}', expected={}", field, invalidValue, expectedType);

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

    @ExceptionHandler({
            BadCredentialsException.class,
            InternalAuthenticationServiceException.class,
            AuthenticationException.class
    })
    public ResponseEntity<ApiEnumErrorResponse> handleAuthentication(AuthenticationException ex) {
        Instant ts = appNow();

        log.warn("Authentication failed: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ApiEnumErrorResponse.builder()
                        .timestamp(ts)
                        .error("INVALID_USER")
                        .message("usuario ou senha invalidos")
                        .build()
        );
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ApiEnumErrorResponse> handleAuthorizationDenied(AuthorizationDeniedException ex) {
        Instant ts = appNow();

        log.warn("Acesso negado (AuthorizationDenied): {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                ApiEnumErrorResponse.builder()
                        .timestamp(ts)
                        .error(ApiErrorCode.FORBIDDEN.name())
                        .message("Acesso negado")
                        .build()
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiEnumErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        Instant ts = appNow();

        log.warn("Acesso negado (AccessDenied): {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                ApiEnumErrorResponse.builder()
                        .timestamp(ts)
                        .error(ApiErrorCode.FORBIDDEN.name())
                        .message("Acesso negado")
                        .build()
        );
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiErrorResponse> handleDomainException(DomainException ex) {
        Instant ts = appNow();

        ApiErrorResponse body = ApiErrorResponse.builder()
                .timestamp(ts)
                .error("DOMAIN_RULE_VIOLATION")
                .message(ex.getMessage())
                .build();

        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiEnumErrorResponse> handleGeneric(Exception ex) {
        Instant ts = appNow();

        log.error("Unhandled exception: {}", ex.getMessage(), ex);

        return ResponseEntity.internalServerError().body(
                ApiEnumErrorResponse.builder()
                        .timestamp(ts)
                        .error("INTERNAL_SERVER_ERROR")
                        .message("Erro interno inesperado. Contate o suporte.")
                        .build()
        );
    }
}