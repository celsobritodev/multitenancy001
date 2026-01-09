package brito.com.multitenancy001.shared.api.error;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestControllerAdvice
public class GlobalExceptionHandler {

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
                        .timestamp(LocalDateTime.now())
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
                .timestamp(LocalDateTime.now())
                .error("INVALID_REQUEST_BODY")
                .message("Corpo da requisição inválido")
                .build()
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiEnumErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        
        String errorMessage = ex.getMostSpecificCause().getMessage();
        
        // Log para debug
        System.out.println("=== DEBUG DataIntegrityViolationException ===");
        System.out.println("Error message: " + errorMessage);
        
        // Verifica qual constraint foi violada
        if (errorMessage.contains("company_doc_number")) {
            String cnpj = extractValue(errorMessage, "company_doc_number");
            return ResponseEntity.status(409).body(
                ApiEnumErrorResponse.builder()
                    .timestamp(LocalDateTime.now())
                    .error("DUPLICATE_CNPJ")
                    .message("Já existe uma conta com o CNPJ " + cnpj)
                    .field("companyDocNumber")
                    .invalidValue(cnpj)
                    .build()
            );
        }
        
        if (errorMessage.contains("company_email")) {
            String email = extractValue(errorMessage, "company_email");
            return ResponseEntity.status(409).body(
                ApiEnumErrorResponse.builder()
                    .timestamp(LocalDateTime.now())
                    .error("DUPLICATE_EMAIL")
                    .message("Já existe uma conta com o email " + email)
                    .field("companyEmail")
                    .invalidValue(email)
                    .build()
            );
        }
        
        if (errorMessage.contains("slug")) {
            String slug = extractValue(errorMessage, "slug");
            return ResponseEntity.status(409).body(
                ApiEnumErrorResponse.builder()
                    .timestamp(LocalDateTime.now())
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
                    .timestamp(LocalDateTime.now())
                    .error("DUPLICATE_SCHEMA")
                    .message("Erro interno: schema " + schema + " já existe")
                    .build()
            );
        }
        
        // Caso genérico
        return ResponseEntity.status(409).body(
            ApiEnumErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .error("DUPLICATE_ENTRY")
                .message("Registro duplicado. Verifique os dados informados.")
                .build()
        );
    }
    
    private String extractValue(String message, String fieldName) {
        try {
            // Padrão para PostgreSQL: "Chave (company_doc_number)=(4254567235667712) já existe."
            Pattern pattern = Pattern.compile("\\(" + fieldName + "\\)=\\(([^\\)]+)\\)");
            Matcher matcher = pattern.matcher(message);
            
            if (matcher.find()) {
                return matcher.group(1);
            }
            
            // Padrão alternativo: "Key (company_doc_number)=(value) already exists."
            Pattern pattern2 = Pattern.compile("Key \\(" + fieldName + "\\)=\\(([^\\)]+)\\)");
            Matcher matcher2 = pattern2.matcher(message);
            
            if (matcher2.find()) {
                return matcher2.group(1);
            }
            
        } catch (Exception e) {
            System.out.println("Erro ao extrair valor: " + e.getMessage());
        }
        
        return "não identificado";
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiEnumErrorResponse> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.getStatus()).body(
            ApiEnumErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .error(ex.getError())
                .message(ex.getMessage())
                .build()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiEnumErrorResponse> handleGeneric(Exception ex) {
        // 🔥 REMOVA os logs de debug do handler genérico
        // System.out.println("=== DEBUG Generic Exception ===");
        // System.out.println("Exception type: " + ex.getClass().getName());
        // System.out.println("Message: " + ex.getMessage());
        // ex.printStackTrace();
        
        return ResponseEntity.internalServerError().body(
            ApiEnumErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .error("INTERNAL_SERVER_ERROR")
                .message("Erro interno inesperado. Contate o suporte.")
                .build()
        );
    }
    
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex) {
        
        List<String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        
        ApiErrorResponse errorResponse = ApiErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .error("VALIDATION_ERROR")
                .message("Erro de validação")
                .details(errors)
                .build();
        
        return ResponseEntity.badRequest().body(errorResponse);
    }
    
    
    
}