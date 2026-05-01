package brito.com.multitenancy001.controlplane.billing.app;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import brito.com.multitenancy001.shared.api.dto.billing.AdminPaymentRequest;
import brito.com.multitenancy001.shared.api.dto.billing.PaymentRequest;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.domain.billing.PaymentPurpose;
import brito.com.multitenancy001.shared.kernel.error.ApiException;

/**
 * Validador semântico de requests de pagamento do Control Plane.
 *
 * <p>Regra V33:</p>
 * <ul>
 *   <li>Sem status HTTP hardcoded.</li>
 *   <li>Validação centralizada.</li>
 * </ul>
 */
@Service
public class ControlPlanePaymentRequestValidator {

    public void validateAdminRequest(AdminPaymentRequest request) {
        requireRequest(request);
        requireAccountId(request.accountId());

        validateCommonUpgradeIdempotency(
                request.purpose(),
                request.idempotencyKey()
        );
    }

    public void validateSelfRequest(PaymentRequest request) {
        requireRequest(request);

        validateCommonUpgradeIdempotency(
                request.purpose(),
                request.idempotencyKey()
        );
    }

    public String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private void requireRequest(Object request) {
        if (request == null) {
            throw new ApiException(
                    ApiErrorCode.INVALID_REQUEST,
                    "Request inválido"
            );
        }
    }

    private void requireAccountId(Long accountId) {
        if (accountId == null) {
            throw new ApiException(
                    ApiErrorCode.INVALID_REQUEST,
                    "accountId obrigatório"
            );
        }
    }

    private void validateCommonUpgradeIdempotency(
            PaymentPurpose purpose,
            String idempotencyKey
    ) {
        if (purpose == PaymentPurpose.PLAN_UPGRADE
                && !StringUtils.hasText(idempotencyKey)) {

            throw new ApiException(
                    ApiErrorCode.INVALID_REQUEST,
                    "idempotencyKey obrigatório para upgrade"
            );
        }
    }
}