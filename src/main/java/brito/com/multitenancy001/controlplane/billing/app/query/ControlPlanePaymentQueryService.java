package brito.com.multitenancy001.controlplane.billing.app.query;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import brito.com.multitenancy001.controlplane.billing.domain.Payment;
import brito.com.multitenancy001.controlplane.billing.persistence.ControlPlanePaymentRepository;
import brito.com.multitenancy001.shared.api.dto.billing.PaymentResponse;
import brito.com.multitenancy001.shared.billing.PaymentQueryService;
import brito.com.multitenancy001.shared.domain.billing.PaymentStatus;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import brito.com.multitenancy001.shared.time.AppClock;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ControlPlanePaymentQueryService implements PaymentQueryService {

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;

    private final ControlPlanePaymentRepository controlPlanePaymentRepository;
    private final AppClock appClock;

    @Override
    public List<PaymentResponse> findByStatus(PaymentStatus status) {
        return publicSchemaUnitOfWork.readOnly(() -> {
            if (status == null) throw new ApiException(ApiErrorCode.PAYMENT_STATUS_REQUIRED, "status é obrigatório", 400);

            return controlPlanePaymentRepository.findByStatus(status)
                    .stream()
                    .map(this::mapToResponse)
                    .toList();
        });
    }

    @Override
    public BigDecimal getTotalPaidInPeriod(Long accountId, Instant startDate, Instant endDate) {
        return publicSchemaUnitOfWork.readOnly(() -> {
            if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);
            if (startDate == null || endDate == null) {
                throw new ApiException(ApiErrorCode.DATE_RANGE_REQUIRED, "startDate/endDate são obrigatórios", 400);
            }

            BigDecimal total = controlPlanePaymentRepository.getTotalPaidInPeriod(accountId, startDate, endDate);
            return total != null ? total : BigDecimal.ZERO;
        });
    }

    @Override
    public long countCompletedPayments(Long accountId) {
        return publicSchemaUnitOfWork.readOnly(() -> {
            if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);

            Long count = controlPlanePaymentRepository.countCompletedPayments(accountId);
            return count != null ? count : 0L;
        });
    }

    @Override
    public List<PaymentResponse> listByAccount(Long accountId) {
        return publicSchemaUnitOfWork.readOnly(() -> {
            if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);

            return controlPlanePaymentRepository.findByAccount_IdOrderByAudit_CreatedAtDesc(accountId)
                    .stream()
                    .map(this::mapToResponse)
                    .toList();
        });
    }

    @Override
    public PaymentResponse getByAccount(Long accountId, Long paymentId) {
        return publicSchemaUnitOfWork.readOnly(() -> {
            if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);
            if (paymentId == null) throw new ApiException(ApiErrorCode.PAYMENT_ID_REQUIRED, "paymentId é obrigatório", 400);

            Payment payment = controlPlanePaymentRepository.findByIdAndAccount_Id(paymentId, accountId)
                    .orElseThrow(() -> new ApiException(ApiErrorCode.PAYMENT_NOT_FOUND, "Pagamento não encontrado", 404));

            return mapToResponse(payment);
        });
    }

    @Override
    public boolean hasActivePayment(Long accountId) {
        return publicSchemaUnitOfWork.readOnly(() -> {
            if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_ID_REQUIRED, "accountId é obrigatório", 400);
            return controlPlanePaymentRepository.existsActivePayment(accountId, appClock.instant());
        });
    }

    private PaymentResponse mapToResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getAccount().getId(),

                payment.getAmount(),
                payment.getPaymentMethod(),
                payment.getPaymentGateway(),
                payment.getStatus(),

                payment.getDescription(),

                payment.getPaymentDate(),
                payment.getValidUntil(),
                payment.getRefundedAt(),

                payment.getAudit() != null ? payment.getAudit().getCreatedAt() : null,
                payment.getAudit() != null ? payment.getAudit().getUpdatedAt() : null
        );
    }
}
