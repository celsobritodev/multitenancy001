package brito.com.multitenancy001.shared.api.error;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
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
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                        ApiEnumErrorResponse.builder()
                                .timestamp(ts)
                                .error(ApiErrorCode.INVALID_ENUM.code())
                                .message("Valor inválido para o campo " + fieldName)
                                .field(fieldName)
                                .invalidValue(invalidValue)
                                .allowedValues(allowedValues)
                                .build()
                );
            }
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                ApiEnumErrorResponse.builder()
                        .timestamp(ts)
                        .error(ApiErrorCode.INVALID_REQUEST_BODY.code())
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
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    ApiEnumErrorResponse.builder()
                            .timestamp(ts)
                            .error(ApiErrorCode.DUPLICATE_NUMBER.code())
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
                            .error(ApiErrorCode.DUPLICATE_EMAIL.code())
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
                            .error(ApiErrorCode.DUPLICATE_SLUG.code())
                            .message("Já existe uma conta com o slug " + slug)
                            .field("slug")
                            .invalidValue(slug)
                            .build()
            );
        }

        if (errorMessage.contains("schema_name")) {
            String schema = extractValue(errorMessage, "schema_name");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(
                    ApiEnumErrorResponse.builder()
                            .timestamp(ts)
                            .error(ApiErrorCode.DUPLICATE_SCHEMA.code())
                            .message("Erro interno: schema " + schema + " já existe")
                            .build()
            );
        }

        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                ApiEnumErrorResponse.builder()
                        .timestamp(ts)
                        .error(ApiErrorCode.DUPLICATE_ENTRY.code())
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
                        .error(ex.getError()) // continua String no JSON (compat)
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
                .error(ApiErrorCode.VALIDATION_ERROR.code())
                .message("Erro de validação")
                .details(errors)
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse);
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
                        .error(ApiErrorCode.INVALID_USER.code())
                        .message("usuario ou senha invalidos")
                        .build()
        );
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiErrorResponse> handleDomainException(DomainException ex) {
        Instant ts = appNow();

        ApiErrorResponse body = ApiErrorResponse.builder()
                .timestamp(ts)
                .error(ApiErrorCode.DOMAIN_RULE_VIOLATION.code())
                .message(ex.getMessage())
                .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiEnumErrorResponse> handleGeneric(Exception ex) {
        Instant ts = appNow();

        log.error("Unhandled exception: {}", ex.getMessage(), ex);

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                ApiEnumErrorResponse.builder()
                        .timestamp(ts)
                        .error(ApiErrorCode.INTERNAL_SERVER_ERROR.code())
                        .message("Erro interno inesperado. Contate o suporte.")
                        .build()
        );
    }
}

