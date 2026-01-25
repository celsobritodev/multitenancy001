package brito.com.multitenancy001.shared.api.error;

import brito.com.multitenancy001.shared.domain.DomainException;
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
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

    private final AppClock appClock;

    private LocalDateTime now() {
        return appClock.now();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiEnumErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {

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
                                .timestamp(now())
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
                        .timestamp(now())
                        .error("INVALID_REQUEST_BODY")
                        .message("Corpo da requisição inválido")
                        .build()
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiEnumErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {

        String errorMessage = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        if (!StringUtils.hasText(errorMessage)) errorMessage = "";

        log.debug("DataIntegrityViolationException: {}", errorMessage);

        if (errorMessage.contains("tax_id_number")) {
            String cnpj = extractValue(errorMessage, "tax_id_number");
            return ResponseEntity.status(409).body(
                    ApiEnumErrorResponse.builder()
                            .timestamp(now())
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
                            .timestamp(now())
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
                            .timestamp(now())
                            .error("DUPLICATE_SLUG")
                            .message("Já existe uma conta com o slug " + slug)
                            .field("slug")
                            .invalidValue(slug)
                            .build()
            );
        }

        if (errorMessage.contains("schema_name")) {
            String schema = extractValue(errorMessage, "schema_name");
            return ResponseEntity.status(409).body(
                    ApiEnumErrorResponse.builder()
                            .timestamp(now())
                            .error("DUPLICATE_SCHEMA")
                            .message("Erro interno: schema " + schema + " já existe")
                            .build()
            );
        }

        return ResponseEntity.status(409).body(
                ApiEnumErrorResponse.builder()
                        .timestamp(now())
                        .error("DUPLICATE_ENTRY")
                        .message("Registro duplicado. Verifique os dados informados.")
                        .build()
        );
    }

    private String extractValue(String message, String fieldName) {
        try {
            // PostgreSQL pt-BR: "Chave (tax_id_number)=(...) já existe."
            Pattern pattern = Pattern.compile("\\(" + Pattern.quote(fieldName) + "\\)=\\(([^\\)]+)\\)");
            Matcher matcher = pattern.matcher(message);
            if (matcher.find()) return matcher.group(1);

            // Alternativo EN: "Key (tax_id_number)=(...) already exists."
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
        return ResponseEntity.status(ex.getStatus()).body(
                ApiEnumErrorResponse.builder()
                        .timestamp(now())
                        .error(ex.getError())
                        .message(ex.getMessage())
                        .build()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationExceptions(MethodArgumentNotValidException ex) {

        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();

        ApiErrorResponse errorResponse = ApiErrorResponse.builder()
                .timestamp(now())
                .error("VALIDATION_ERROR")
                .message("Erro de validação")
                .details(errors)
                .build();

        return ResponseEntity.badRequest().body(errorResponse);
    }
    
    
    /**
     * ✅ Login inválido (não vazar se o usuário existe).
     * - senha errada -> BadCredentialsException
     * - userDetails falha -> InternalAuthenticationServiceException (wrap do Spring)
     * - qualquer falha auth -> AuthenticationException
     */
    @ExceptionHandler({
            BadCredentialsException.class,
            InternalAuthenticationServiceException.class,
            AuthenticationException.class
    })
    public ResponseEntity<ApiEnumErrorResponse> handleAuthentication(AuthenticationException ex) {

        // log detalhado só no server (ajuda debug sem expor para o cliente)
        log.warn("Authentication failed: {}", ex.getMessage());

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                ApiEnumErrorResponse.builder()
                        .timestamp(now())
                        .error("INVALID_USER")
                        .message("usuario ou senha invalidos")
                        .build()
        );
    }

    

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiEnumErrorResponse> handleGeneric(Exception ex) {
        // log só o suficiente (sem stacktrace enorme por padrão)
        log.error("Unhandled exception: {}", ex.getMessage(), ex);

        return ResponseEntity.internalServerError().body(
                ApiEnumErrorResponse.builder()
                        .timestamp(now())
                        .error("INTERNAL_SERVER_ERROR")
                        .message("Erro interno inesperado. Contate o suporte.")
                        .build()
        );
    }
    
    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiErrorResponse> handleDomainException(DomainException ex) {
        ApiErrorResponse body = ApiErrorResponse.builder()
                .timestamp(now())
                .error("DOMAIN_RULE_VIOLATION")
                .message(ex.getMessage())
                .build();

        return ResponseEntity.badRequest().body(body);
    }

}
