package brito.com.multitenancy001.controlplane.billing.app.query;

import brito.com.multitenancy001.controlplane.billing.domain.Payment;
import brito.com.multitenancy001.controlplane.billing.persistence.ControlPlanePaymentRepository;
import brito.com.multitenancy001.shared.api.dto.billing.PaymentResponse;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
import brito.com.multitenancy001.shared.billing.PaymentQueryService;
import brito.com.multitenancy001.shared.domain.billing.PaymentStatus;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Query Service do Control Plane para Payments.
 *
 * <p>Regra V33:</p>
 * <ul>
 *   <li>Sem status HTTP hardcoded.</li>
 *   <li>Validação encapsulada.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ControlPlanePaymentQueryService implements PaymentQueryService {

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final ControlPlanePaymentRepository controlPlanePaymentRepository;
    private final AppClock appClock;

    @Override
    public List<PaymentResponse> findByStatus(PaymentStatus status) {
        return publicSchemaUnitOfWork.readOnly(() -> {

            requireStatus(status);

            return controlPlanePaymentRepository.findByStatus(status)
                    .stream()
                    .map(this::mapToResponse)
                    .toList();
        });
    }

    @Override
    public BigDecimal getTotalPaidInPeriod(Long accountId, Instant startDate, Instant endDate) {
        return publicSchemaUnitOfWork.readOnly(() -> {

            requireAccountId(accountId);
            requireDateRange(startDate, endDate);

            BigDecimal total = controlPlanePaymentRepository.getTotalPaidInPeriod(accountId, startDate, endDate);
            return total != null ? total : BigDecimal.ZERO;
        });
    }

    @Override
    public long countCompletedPayments(Long accountId) {
        return publicSchemaUnitOfWork.readOnly(() -> {

            requireAccountId(accountId);

            Long count = controlPlanePaymentRepository.countCompletedPayments(accountId);
            return count != null ? count : 0L;
        });
    }

    @Override
    public List<PaymentResponse> listByAccount(Long accountId) {
        return publicSchemaUnitOfWork.readOnly(() -> {

            requireAccountId(accountId);

            return controlPlanePaymentRepository
                    .findByAccount_IdOrderByAudit_CreatedAtDesc(accountId)
                    .stream()
                    .map(this::mapToResponse)
                    .toList();
        });
    }

    @Override
    public PaymentResponse getByAccount(Long accountId, Long paymentId) {
        return publicSchemaUnitOfWork.readOnly(() -> {

            requireAccountId(accountId);
            requirePaymentId(paymentId);

            Payment payment = controlPlanePaymentRepository
                    .findByIdAndAccount_Id(paymentId, accountId)
                    .orElseThrow(() -> new ApiException(
                            ApiErrorCode.PAYMENT_NOT_FOUND,
                            "Pagamento não encontrado"
                    ));

            return mapToResponse(payment);
        });
    }

    @Override
    public boolean hasActivePayment(Long accountId) {
        return publicSchemaUnitOfWork.readOnly(() -> {

            requireAccountId(accountId);

            return controlPlanePaymentRepository.existsActivePayment(accountId, appClock.instant());
        });
    }

    // =========================
    // VALIDADORES PRIVADOS V33
    // =========================

    private void requireStatus(PaymentStatus status) {
        if (status == null) {
            throw new ApiException(
                    ApiErrorCode.PAYMENT_STATUS_REQUIRED,
                    "status é obrigatório"
            );
        }
    }

    private void requireAccountId(Long accountId) {
        if (accountId == null) {
            throw new ApiException(
                    ApiErrorCode.ACCOUNT_ID_REQUIRED,
                    "accountId é obrigatório"
            );
        }
    }

    private void requirePaymentId(Long paymentId) {
        if (paymentId == null) {
            throw new ApiException(
                    ApiErrorCode.PAYMENT_ID_REQUIRED,
                    "paymentId é obrigatório"
            );
        }
    }

    private void requireDateRange(Instant startDate, Instant endDate) {
        if (startDate == null || endDate == null) {
            throw new ApiException(
                    ApiErrorCode.DATE_RANGE_REQUIRED,
                    "startDate/endDate são obrigatórios"
            );
        }
    }

    // =========================
    // MAPPER
    // =========================

    private PaymentResponse mapToResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getAccount().getId(),

                payment.getAmount(),
                payment.getPaymentMethod(),
                payment.getPaymentGateway(),
                payment.getStatus(),

                payment.getDescription(),

                payment.getTargetPlan(),
                payment.getBillingCycle(),
                payment.getPaymentPurpose(),
                payment.getPlanPriceSnapshot(),
                payment.getCurrency(),
                payment.getEffectiveFrom(),
                payment.getCoverageEndDate(),

                payment.getPaymentDate(),
                payment.getValidUntil(),
                payment.getRefundedAt(),

                payment.getAudit() != null ? payment.getAudit().getCreatedAt() : null,
                payment.getAudit() != null ? payment.getAudit().getUpdatedAt() : null
        );
    }
}