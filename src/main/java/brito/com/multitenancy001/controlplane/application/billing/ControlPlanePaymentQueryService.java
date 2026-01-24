package brito.com.multitenancy001.controlplane.application.billing;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import brito.com.multitenancy001.controlplane.domain.billing.Payment;
import brito.com.multitenancy001.controlplane.persistence.billing.ControlPlanePaymentRepository;
import brito.com.multitenancy001.shared.api.dto.billing.PaymentResponse;
import brito.com.multitenancy001.shared.api.error.ApiException;
import brito.com.multitenancy001.shared.domain.billing.PaymentStatus;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ControlPlanePaymentQueryService {

    private final ControlPlanePaymentRepository paymentRepository;
    private final brito.com.multitenancy001.shared.time.AppClock appClock;

    

    @Transactional(readOnly = true)
    public List<PaymentResponse> findByStatus(PaymentStatus status) {
        if (status == null) throw new ApiException("PAYMENT_STATUS_REQUIRED", "status é obrigatório", 400);

        return paymentRepository.findByStatus(status)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BigDecimal getTotalPaidInPeriod(Long accountId, LocalDateTime startDate, LocalDateTime endDate) {
        if (accountId == null) throw new ApiException("ACCOUNT_ID_REQUIRED", "accountId é obrigatório", 400);
        if (startDate == null || endDate == null) throw new ApiException("DATE_RANGE_REQUIRED", "startDate/endDate são obrigatórios", 400);

        BigDecimal total = paymentRepository.getTotalPaidInPeriod(accountId, startDate, endDate);
        return total != null ? total : BigDecimal.ZERO;
    }

    @Transactional(readOnly = true)
    public long countCompletedPayments(Long accountId) {
        if (accountId == null) throw new ApiException("ACCOUNT_ID_REQUIRED", "accountId é obrigatório", 400);

        Long count = paymentRepository.countCompletedPayments(accountId);
        return count != null ? count : 0L;
    }
    
    
    @Transactional(readOnly = true)
    public List<PaymentResponse> listByAccount(Long accountId) {
        if (accountId == null) throw new ApiException("ACCOUNT_ID_REQUIRED", "accountId é obrigatório", 400);

        return paymentRepository.findByAccountIdOrderByCreatedAtDesc(accountId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PaymentResponse getByAccount(Long accountId, Long paymentId) {
        if (accountId == null) throw new ApiException("ACCOUNT_ID_REQUIRED", "accountId é obrigatório", 400);
        if (paymentId == null) throw new ApiException("PAYMENT_ID_REQUIRED", "paymentId é obrigatório", 400);

        Payment payment = paymentRepository.findByIdAndAccountId(paymentId, accountId)
                .orElseThrow(() -> new ApiException("PAYMENT_NOT_FOUND", "Pagamento não encontrado", 404));

        return mapToResponse(payment);
    }

    /**
     * "Active payment" no seu domínio: eu vou assumir que significa
     * existir pagamento com status COMPLETED e validUntil >= now.
     * Ajuste aqui se sua regra for diferente (ex.: status=ACTIVE).
     */
    @Transactional(readOnly = true)
    public boolean hasActivePayment(Long accountId) {
        if (accountId == null) throw new ApiException("ACCOUNT_ID_REQUIRED", "accountId é obrigatório", 400);

        return paymentRepository.existsActivePayment(accountId, appClock.now());

    }


    private PaymentResponse mapToResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getAccount().getId(),
                payment.getAmount(),
                payment.getPaymentDate(),
                payment.getValidUntil(),
                payment.getStatus(),
                payment.getTransactionId(),
                payment.getPaymentMethod(),
                payment.getPaymentGateway(),
                payment.getDescription(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
