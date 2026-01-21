package brito.com.multitenancy001.shared.contracts.billing;

import brito.com.multitenancy001.shared.api.dto.billing.PaymentResponse;

import java.util.List;

public interface AccountPaymentsReader {

    List<PaymentResponse> listPaymentsForAccount(Long accountId);

    PaymentResponse getPaymentForAccount(Long accountId, Long paymentId);

    boolean hasActivePayment(Long accountId);
}
