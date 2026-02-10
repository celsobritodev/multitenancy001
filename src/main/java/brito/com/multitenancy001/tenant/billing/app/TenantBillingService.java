package brito.com.multitenancy001.tenant.billing.app;

import brito.com.multitenancy001.infrastructure.persistence.tx.PublicReadOnlyTx;
import brito.com.multitenancy001.shared.api.dto.billing.PaymentResponse;
import brito.com.multitenancy001.shared.billing.PaymentQueryFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TenantBillingService {

    private final PaymentQueryFacade paymentQueryFacade;

    @PublicReadOnlyTx
    public List<PaymentResponse> listPaymentsForAccount(Long accountId) {
        return paymentQueryFacade.listByAccount(accountId);
    }

    @PublicReadOnlyTx
    public PaymentResponse getPaymentForAccount(Long accountId, Long paymentId) {
        return paymentQueryFacade.getByAccount(accountId, paymentId);
    }

    @PublicReadOnlyTx
    public boolean hasActivePayment(Long accountId) {
        return paymentQueryFacade.hasActivePayment(accountId);
    }
}
