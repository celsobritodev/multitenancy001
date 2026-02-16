package brito.com.multitenancy001.shared.kernel.error;

import brito.com.multitenancy001.shared.api.error.ApiErrorCategory;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import lombok.Getter;

import java.util.List;
import java.util.Objects;

/**
 * Exceção padrão de API (VERSÃO ENFORCED).
 *
 * Regras:
 * - NÃO existe mais construtor legado com String code.
 * - O "error" do payload é sempre code.name().
 * - O status default vem de ApiErrorCode.httpStatus() quando não informado.
 */
@Getter
public class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Código exposto no payload (sempre enum.name()).
     */
    private final String error;

    /**
     * Status HTTP a ser retornado.
     */
    private final int status;

    /**
     * Campos adicionais (quando aplicável).
     */
    private final String field;
    private final String invalidValue;
    private final List<String> allowedValues;
    private final Object details;

    /**
     * Metadados tipados.
     */
    private final ApiErrorCode code;
    private final ApiErrorCategory category;

    // =========================
    // Construtores (preferidos)
    // =========================

    public ApiException(ApiErrorCode code) {
        this(code, null, 0, null, null, null, null);
    }

    public ApiException(ApiErrorCode code, String message) {
        this(code, message, 0, null, null, null, null);
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
        super(resolveMessage(code, message));

        ApiErrorCode safeCode = (code != null ? code : ApiErrorCode.INTERNAL_SERVER_ERROR);

        this.code = safeCode;
        this.category = safeCode.category();

        this.error = safeCode.name();
        this.status = resolveStatus(safeCode, status);

        this.field = field;
        this.invalidValue = invalidValue;
        this.allowedValues = allowedValues;
        this.details = details;
    }

    // =========================
    // Helpers
    // =========================

    private static String resolveMessage(ApiErrorCode code, String message) {
        if (message != null && !message.isBlank()) return message;
        if (code != null && code.defaultMessage() != null && !code.defaultMessage().isBlank()) return code.defaultMessage();
        return "Erro";
    }

    private static int resolveStatus(ApiErrorCode code, int status) {
        if (status > 0) return status;
        return Objects.requireNonNull(code, "code").httpStatus();
    }
}
