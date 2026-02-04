package brito.com.multitenancy001.tenant.billing.app;

import brito.com.multitenancy001.shared.api.dto.billing.PaymentResponse;
import brito.com.multitenancy001.shared.billing.PaymentQueryFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TenantBillingService {

    private final PaymentQueryFacade paymentQueryFacade;

    @Transactional(readOnly = true)
    public List<PaymentResponse> listPaymentsForAccount(Long accountId) {
        return paymentQueryFacade.listByAccount(accountId);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentForAccount(Long accountId, Long paymentId) {
        return paymentQueryFacade.getByAccount(accountId, paymentId);
    }

    @Transactional(readOnly = true)
    public boolean hasActivePayment(Long accountId) {
        return paymentQueryFacade.hasActivePayment(accountId);
    }
}

