package brito.com.multitenancy001.shared.api.error;

import lombok.Getter;

import java.util.List;

@Getter
public class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String error;
    private final int status;

    // ✅ extras padronizados (opcionais)
    private final String field;
    private final String invalidValue;
    private final List<String> allowedValues;
    private final Object details;

    public ApiException(String error, String message, int status) {
        this(error, message, status, null, null, null, null);
    }

    public ApiException(String error, String message, int status, Object details) {
        this(error, message, status, null, null, null, details);
    }

    public ApiException(
            String error,
            String message,
            int status,
            String field,
            String invalidValue,
            List<String> allowedValues,
            Object details
    ) {
        super(message);
        this.error = error;
        this.status = status;
        this.field = field;
        this.invalidValue = invalidValue;
        this.allowedValues = allowedValues;
        this.details = details;
    }
}
