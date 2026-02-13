package brito.com.multitenancy001.shared.kernel.error;

import brito.com.multitenancy001.shared.api.error.ApiErrorCategory;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import lombok.Getter;

import java.util.List;

/**
 * Exceção padrão de API.
 *
 * COMPATIBILIDADE:
 * - mantém o construtor legado (String error, String message, int status)
 * - adiciona construtores com ApiErrorCode
 *
 * Objetivo:
 * - permitir migração incremental de "String codes" para enum (ApiErrorCode)
 *   sem quebrar controllers/handlers existentes.
 */
@Getter
public class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Código exposto no payload atual.
     * Para enum: error = code.name()
     */
    private final String error;

    /**
     * Status HTTP a ser retornado.
     */
    private final int status;

    /**
     * Campos adicionais usados por ApiEnumErrorResponse (quando aplicável).
     */
    private final String field;
    private final String invalidValue;
    private final List<String> allowedValues;
    private final Object details;

    /**
     * Metadados tipados (novos, opcionais).
     * Não quebram o contrato atual porque não precisam ser serializados.
     */
    private final ApiErrorCode code;
    private final ApiErrorCategory category;

    // =========================
    // NOVO: enum (preferido)
    // =========================

    public ApiException(ApiErrorCode code) {
        this(code, code != null ? code.defaultMessage() : null, code != null ? code.httpStatus() : 500, null, null, null, null);
    }

    public ApiException(ApiErrorCode code, String message) {
        this(code, message, code != null ? code.httpStatus() : 500, null, null, null, null);
    }

    public ApiException(ApiErrorCode code, String message, int status) {
        this(code, message, status, null, null, null, null);
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

        this.code = code;
        this.category = (code != null ? code.category() : ApiErrorCategory.INTERNAL);

        this.error = (code != null ? code.name() : ApiErrorCode.INTERNAL_SERVER_ERROR.name());
        this.status = status;

        this.field = field;
        this.invalidValue = invalidValue;
        this.allowedValues = allowedValues;
        this.details = details;
    }

    // =========================
    // LEGADO: String code
    // =========================

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

        String safeError = (error != null && !error.isBlank()) ? error.trim() : ApiErrorCode.INTERNAL_SERVER_ERROR.name();
        this.error = safeError;
        this.status = status;

        this.code = ApiErrorCode.tryParse(safeError).orElse(null);
        this.category = (this.code != null ? this.code.category() : ApiErrorCategory.INTERNAL);

        this.field = field;
        this.invalidValue = invalidValue;
        this.allowedValues = allowedValues;
        this.details = details;
    }
}
