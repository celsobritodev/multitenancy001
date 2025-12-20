package brito.com.multitenancy001.exceptions;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.JsonMappingException;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {

        Throwable cause = ex;

        while (cause != null) {

            if (cause instanceof InvalidFormatException ife &&
                ife.getTargetType().isEnum()) {

                String fieldName = "status";

                if (!ife.getPath().isEmpty()) {
                    JsonMappingException.Reference ref = ife.getPath().get(0);
                    fieldName = ref.getFieldName();
                }

                List<String> allowedValues =
                        Arrays.stream(ife.getTargetType().getEnumConstants())
                                .map(Object::toString)
                                .toList();

                return ResponseEntity.badRequest().body(
                        ErrorResponse.builder()
                                .timestamp(LocalDateTime.now())
                                .error("INVALID_ENUM_VALUE")
                                .message("Valor inválido para o campo '" + fieldName + "'")
                                .details(allowedValues)
                                .build()
                );
            }

            cause = cause.getCause();
        }

        return ResponseEntity.badRequest().body(
                ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .error("INVALID_REQUEST_BODY")
                        .message("Corpo da requisição inválido")
                        .build()
        );
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.getStatus()).body(
                ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .error(ex.getError())
                        .message(ex.getMessage())
                        .build()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        return ResponseEntity.internalServerError().body(
                ErrorResponse.builder()
                        .timestamp(LocalDateTime.now())
                        .error("INTERNAL_SERVER_ERROR")
                        .message("Erro interno inesperado. Contate o suporte.")
                        .build()
        );
    }
}
