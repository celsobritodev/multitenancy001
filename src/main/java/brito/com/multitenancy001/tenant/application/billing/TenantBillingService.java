package brito.com.multitenancy001.tenant.application.billing;

import brito.com.multitenancy001.controlplane.application.billing.ControlPlanePaymentQueryService;
import brito.com.multitenancy001.shared.api.dto.billing.PaymentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TenantBillingService {

    private final ControlPlanePaymentQueryService controlPlanePaymentQueryService;

    @Transactional(readOnly = true)
    public List<PaymentResponse> listPaymentsForAccount(Long accountId) {
        return controlPlanePaymentQueryService.listByAccount(accountId);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentForAccount(Long accountId, Long paymentId) {
        return controlPlanePaymentQueryService.getByAccount(accountId, paymentId);
    }

    @Transactional(readOnly = true)
    public boolean hasActivePayment(Long accountId) {
        return controlPlanePaymentQueryService.hasActivePayment(accountId);
    }
}
