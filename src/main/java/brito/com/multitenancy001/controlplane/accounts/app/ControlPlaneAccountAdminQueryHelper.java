package brito.com.multitenancy001.controlplane.accounts.app;

import java.time.Instant;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.kernel.error.ApiException;

@Component
public class ControlPlaneAccountAdminQueryHelper {

    public void assertValidCreatedBetweenRange(Instant start, Instant end) {
        if (start == null || end == null) {
            throw new ApiException(
                    ApiErrorCode.INVALID_RANGE,
                    "start/end são obrigatórios"
            );
        }

        if (end.isBefore(start)) {
            throw new ApiException(
                    ApiErrorCode.INVALID_RANGE,
                    "end deve ser >= start"
            );
        }
    }

    public Pageable normalizePageable(Pageable pageable) {
        if (pageable == null) {
            return PageRequest.of(0, 20);
        }
        return pageable;
    }
}