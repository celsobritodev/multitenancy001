package brito.com.multitenancy001.shared.api.error;

import lombok.Getter;

@Getter
public class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private final String error;
    private final int status;
    private final Object details;
    private final Object[] allowedValues; // 🔥 NOVO CAMPO

    // Construtor sem allowedValues
    public ApiException(String error, String message, int status) {
        super(message);
        this.error = error;
        this.status = status;
        this.details = null;
        this.allowedValues = null;
    }

    // Construtor com details
    public ApiException(String error, String message, int status, Object details) {
        super(message);
        this.error = error;
        this.status = status;
        this.details = details;
        this.allowedValues = null;
    }

    // 🔥 NOVO CONSTRUTOR com allowedValues
    public ApiException(String error, String message, int status, Object details, Object[] allowedValues) {
        super(message);
        this.error = error;
        this.status = status;
        this.details = details;
        this.allowedValues = allowedValues;
    }

    // Método para obter allowedValues
    public Object[] getAllowedValues() {
        return allowedValues;
    }

    // Método para verificar se tem allowedValues
    public boolean hasAllowedValues() {
        return allowedValues != null && allowedValues.length > 0;
    }
    
    
    
}