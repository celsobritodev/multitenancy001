package brito.com.multitenancy001.controlplane.accounts.app;

import java.time.Duration;
import java.time.Instant;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;

/**
 * Componente de apoio para consultas administrativas de Account.
 *
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Normalizar paginação.</li>
 *   <li>Validar intervalos de datas para consultas administrativas.</li>
 * </ul>
 */
@Component
public class ControlPlaneAccountAdminQueryHelper {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;
    private static final long MAX_CREATED_BETWEEN_DAYS = 90L;

    /**
     * Normaliza paginação para evitar abuso e manter consistência.
     *
     * @param pageable paginação informada
     * @return paginação normalizada
     */
    public Pageable normalizePageable(Pageable pageable) {
        if (pageable == null) {
            return PageRequest.of(0, DEFAULT_PAGE_SIZE);
        }

        int page = Math.max(0, pageable.getPageNumber());
        int size = pageable.getPageSize();

        if (size <= 0) {
            size = DEFAULT_PAGE_SIZE;
        }

        if (size > MAX_PAGE_SIZE) {
            size = MAX_PAGE_SIZE;
        }

        return PageRequest.of(page, size, pageable.getSort());
    }

    /**
     * Valida intervalo de datas para consulta por criação.
     *
     * @param start início
     * @param end fim
     */
    public void assertValidCreatedBetweenRange(Instant start, Instant end) {
        if (start == null || end == null) {
            throw new ApiException(ApiErrorCode.INVALID_RANGE, "start/end são obrigatórios", 400);
        }

        if (end.isBefore(start)) {
            throw new ApiException(ApiErrorCode.INVALID_RANGE, "end deve ser >= start", 400);
        }

        Duration duration = Duration.between(start, end);
        if (duration.toDays() > MAX_CREATED_BETWEEN_DAYS) {
            throw new ApiException(
                    ApiErrorCode.RANGE_TOO_LARGE,
                    "Intervalo máximo é " + MAX_CREATED_BETWEEN_DAYS + " dias",
                    400
            );
        }
    }
}