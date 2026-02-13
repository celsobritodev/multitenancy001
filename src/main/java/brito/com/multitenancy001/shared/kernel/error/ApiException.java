package brito.com.multitenancy001.shared.kernel.error;

import brito.com.multitenancy001.shared.api.error.ApiErrorCategory;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import lombok.Getter;

import java.util.List;

/**
 * Exceção padronizada para erros de API (enum-only).
 *
 * Regras:
 * - O "código" é SEMPRE um {@link ApiErrorCode} (fonte de verdade).
 * - O status HTTP é SEMPRE o status padrão do {@link ApiErrorCode}.
 * - Não aceita status custom por chamada (evita divergência e "mágica").
 */
@Getter
public class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ApiErrorCode errorCode;

    /**
     * Compatibilidade: campo JSON "error" como String (code textual do enum).
     */
    private final String error;

    /**
     * Status HTTP numérico (sempre = errorCode.defaultHttpStatus()).
     */
    private final int status;

    // extras padronizados (opcionais)
    private final String field;
    private final String invalidValue;
    private final List<String> allowedValues;

    // payload extra (ex.: lista de tenants para seleção, etc.)
    private final Object details;

    public ApiException(ApiErrorCode code) {
        this(code, defaultMessageFor(code), null, null, null, null);
    }

    public ApiException(ApiErrorCode code, String message) {
        this(code, message, null, null, null, null);
    }

    public ApiException(ApiErrorCode code, String message, Object details) {
        this(code, message, null, null, null, details);
    }

    public ApiException(
            ApiErrorCode code,
            String message,
            String field,
            String invalidValue,
            List<String> allowedValues,
            Object details
    ) {
        super(message);

        ApiErrorCode resolved = (code != null ? code : ApiErrorCode.INTERNAL_SERVER_ERROR);

        this.errorCode = resolved;
        this.error = resolved.code();
        this.status = resolved.defaultHttpStatus();

        this.field = field;
        this.invalidValue = invalidValue;
        this.allowedValues = allowedValues;

        this.details = details;
    }

    public ApiErrorCategory getCategory() {
        return errorCode.category();
    }

    private static String defaultMessageFor(ApiErrorCode code) {
        ApiErrorCode resolved = (code != null ? code : ApiErrorCode.INTERNAL_SERVER_ERROR);
        // Mensagem default determinística e governável (sem string mágica espalhada)
        return resolved.code();
    }
}
