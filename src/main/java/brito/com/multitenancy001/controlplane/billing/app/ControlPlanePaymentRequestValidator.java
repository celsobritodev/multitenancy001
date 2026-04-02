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
 * <p>Responsabilidades:</p>
 * <ul>
 *   <li>Validar request administrativo.</li>
 *   <li>Validar request self-service.</li>
 *   <li>Garantir obrigatoriedade de {@code idempotencyKey}
 *       para operações críticas de upgrade.</li>
 *   <li>Centralizar normalização simples de strings de entrada.</li>
 * </ul>
 */
@Service
public class ControlPlanePaymentRequestValidator {

    /**
     * Valida request administrativo.
     *
     * @param adminPaymentRequest request administrativo
     */
    public void validateAdminRequest(AdminPaymentRequest adminPaymentRequest) {
        if (adminPaymentRequest == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "Request inválido", 400);
        }

        if (adminPaymentRequest.accountId() == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "accountId obrigatório", 400);
        }

        validateCommonUpgradeIdempotency(
                adminPaymentRequest.purpose(),
                adminPaymentRequest.idempotencyKey()
        );
    }

    /**
     * Valida request self-service.
     *
     * @param paymentRequest request self-service
     */
    public void validateSelfRequest(PaymentRequest paymentRequest) {
        if (paymentRequest == null) {
            throw new ApiException(ApiErrorCode.INVALID_REQUEST, "Request inválido", 400);
        }

        validateCommonUpgradeIdempotency(
                paymentRequest.purpose(),
                paymentRequest.idempotencyKey()
        );
    }

    /**
     * Normaliza string opcional.
     *
     * @param value valor bruto
     * @return valor normalizado ou {@code null}
     */
    public String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * Garante obrigatoriedade de chave idempotente para upgrade de plano.
     *
     * @param paymentPurpose finalidade do pagamento
     * @param idempotencyKey chave recebida
     */
    private void validateCommonUpgradeIdempotency(
            PaymentPurpose paymentPurpose,
            String idempotencyKey
    ) {
        if (paymentPurpose == PaymentPurpose.PLAN_UPGRADE
                && !StringUtils.hasText(idempotencyKey)) {
            throw new ApiException(
                    ApiErrorCode.INVALID_REQUEST,
                    "idempotencyKey obrigatório para upgrade",
                    400
            );
        }
    }
}