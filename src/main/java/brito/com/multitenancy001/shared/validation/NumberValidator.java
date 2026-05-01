package brito.com.multitenancy001.shared.validation;

import java.math.BigDecimal;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.extern.slf4j.Slf4j;

/**
 * Validador central para números.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Validar ranges numéricos reutilizáveis.</li>
 *   <li>Eliminar validação inline repetitiva.</li>
 *   <li>Padronizar mensagens amigáveis.</li>
 * </ul>
 */
@Slf4j
public final class NumberValidator {

    private static final BigDecimal MAX_SUPPLIER_RATING = new BigDecimal("9.99");

    private NumberValidator() {
    }

    /**
     * Valida lead time não negativo.
     *
     * @param leadTimeDays lead time
     */
    public static void validateNonNegativeLeadTime(Integer leadTimeDays) {
        if (leadTimeDays != null && leadTimeDays < 0) {
            log.warn("❌ Lead time inválido | leadTimeDays={}", leadTimeDays);
            throw new ApiException(
                    ApiErrorCode.INVALID_LEAD_TIME,
                    "leadTimeDays não pode ser negativo"
            );
        }
    }

    /**
     * Valida rating de supplier.
     *
     * @param rating rating
     */
    public static void validateSupplierRating(BigDecimal rating) {
        if (rating == null) {
            return;
        }

        if (rating.compareTo(BigDecimal.ZERO) < 0) {
            log.warn("❌ Rating inválido | rating={}", rating);
            throw new ApiException(
                    ApiErrorCode.INVALID_RATING,
                    "rating não pode ser negativo"
            );
        }

        if (rating.compareTo(MAX_SUPPLIER_RATING) > 0) {
            log.warn("❌ Rating inválido | rating={} | max={}", rating, MAX_SUPPLIER_RATING);
            throw new ApiException(
                    ApiErrorCode.INVALID_RATING,
                    "rating máximo é 9.99"
            );
        }
    }

    /**
     * Exige que valor long seja não negativo.
     *
     * @param value valor
     * @param field nome amigável do campo
     */
    public static void requireNonNegative(long value, String field) {
        if (value < 0L) {
            throw new ApiException(
                    ApiErrorCode.INVALID_REQUEST,
                    field + " não pode ser negativo"
            );
        }
    }

    /**
     * Exige que valor inteiro seja não negativo.
     *
     * @param value valor
     * @param field nome amigável do campo
     */
    public static void requireNonNegative(int value, String field) {
        if (value < 0) {
            throw new ApiException(
                    ApiErrorCode.INVALID_REQUEST,
                    field + " não pode ser negativo"
            );
        }
    }
}