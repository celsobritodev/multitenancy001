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
 * - O "error" do payload é sempre code.name().
 * - O status default vem de ApiErrorCode.httpStatus().
 * - Não existe construtor público que permita "inventar" status no call site.
 * - A mensagem default vem de ApiErrorCode.defaultMessage() quando não informada.
 */
@Getter
public class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Código exposto no payload (sempre enum.name()). */
    private final String error;

    /** Status HTTP a ser retornado (sempre derivado do enum). */
    private final int status;

    /** Campos adicionais (quando aplicável). */
    private final String field;
    private final String invalidValue;
    private final List<String> allowedValues;
    private final Object details;

    /** Metadados tipados. */
    private final ApiErrorCode code;
    private final ApiErrorCategory category;

    // =========================
    // Construtores (enforced)
    // =========================

    public ApiException(ApiErrorCode code) {
        this(code, null, null, null, null, null);
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
        super(resolveMessage(code, message));

        ApiErrorCode safeCode = (code != null ? code : ApiErrorCode.INTERNAL_SERVER_ERROR);

        this.code = safeCode;
        this.category = safeCode.category();

        this.error = safeCode.name();
        this.status = resolveStatus(safeCode);

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

    private static int resolveStatus(ApiErrorCode code) {
        return Objects.requireNonNull(code, "code").httpStatus();
    }
}