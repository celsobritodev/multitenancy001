package brito.com.multitenancy001.shared.kernel.error;

import brito.com.multitenancy001.shared.api.error.ApiErrorCategory;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import lombok.Getter;

import java.util.List;

@Getter
public class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * ✅ NOVO: código tipado (fonte de verdade)
     */
    private final ApiErrorCode errorCode;

    /**
     * ✅ Compatibilidade: seu JSON atual usa "error" como String
     */
    private final String error;

    /**
     * ✅ HTTP status numérico
     */
    private final int status;

    // ✅ extras padronizados (opcionais)
    private final String field;
    private final String invalidValue;
    private final List<String> allowedValues;

    // ✅ qualquer payload extra (ex.: lista de tenants para seleção, etc.)
    private final Object details;

    // ==========================================================
    // Construtores NOVOS (tipados)
    // ==========================================================

    public ApiException(ApiErrorCode code, String message) {
        this(code, message, code != null ? code.defaultHttpStatus() : 500, null, null, null, null);
    }

    public ApiException(ApiErrorCode code, String message, int status) {
        this(code, message, status, null, null, null, null);
    }

    public ApiException(ApiErrorCode code, String message, int status, Object details) {
        this(code, message, status, null, null, null, details);
    }

    public ApiException(
            ApiErrorCode code,
            String message,
            int status,
            String field,
            String invalidValue,
            List<String> allowedValues,
            Object details
    ) {
        super(message);
        this.errorCode = code;
        this.error = (code != null ? code.code() : "INTERNAL_SERVER_ERROR");
        this.status = status;
        this.field = field;
        this.invalidValue = invalidValue;
        this.allowedValues = allowedValues;
        this.details = details;
    }

    // ==========================================================
    // Construtores LEGADOS (String) — para não quebrar tudo agora
    // ==========================================================

    /**
     * @deprecated Migre para ApiException(ApiErrorCode, ...)
     */
    @Deprecated
    public ApiException(String error, String message, int status) {
        this(tryResolve(error), message, status, null, null, null, null);
    }

    /**
     * @deprecated Migre para ApiException(ApiErrorCode, ...)
     */
    @Deprecated
    public ApiException(String error, String message, int status, Object details) {
        this(tryResolve(error), message, status, null, null, null, details);
    }

    /**
     * @deprecated Migre para ApiException(ApiErrorCode, ...)
     */
    @Deprecated
    public ApiException(
            String error,
            String message,
            int status,
            String field,
            String invalidValue,
            List<String> allowedValues,
            Object details
    ) {
        this(tryResolve(error), message, status, field, invalidValue, allowedValues, details);
    }

    private static ApiErrorCode tryResolve(String code) {
        if (code == null || code.isBlank()) return ApiErrorCode.INTERNAL_SERVER_ERROR;
        try {
            return ApiErrorCode.valueOf(code.trim());
        } catch (Exception ignored) {
            // fallback compatível com o legado:
            return ApiErrorCode.INTERNAL_SERVER_ERROR;
        }
    }

    public ApiErrorCategory getCategory() {
        return (errorCode != null ? errorCode.category() : ApiErrorCategory.SYSTEM);
    }
}
