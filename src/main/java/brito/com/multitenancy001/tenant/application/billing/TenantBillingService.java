package brito.com.multitenancy001.tenant.application.billing;


import brito.com.multitenancy001.shared.api.dto.billing.PaymentResponse;
import brito.com.multitenancy001.shared.contracts.billing.AccountPaymentsReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TenantBillingService {

    private final AccountPaymentsReader accountPaymentsReader;

    @Transactional(readOnly = true)
    public List<PaymentResponse> listPaymentsForAccount(Long accountId) {
        return accountPaymentsReader.listPaymentsForAccount(accountId);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentForAccount(Long accountId, Long paymentId) {
        return accountPaymentsReader.getPaymentForAccount(accountId, paymentId);
    }

    @Transactional(readOnly = true)
    public boolean hasActivePayment(Long accountId) {
        return accountPaymentsReader.hasActivePayment(accountId);
    }
}
