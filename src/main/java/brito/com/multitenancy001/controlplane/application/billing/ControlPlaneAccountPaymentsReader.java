package brito.com.multitenancy001.controlplane.application.billing;

import brito.com.multitenancy001.shared.api.dto.billing.PaymentResponse;
import brito.com.multitenancy001.shared.contracts.billing.AccountPaymentsReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ControlPlaneAccountPaymentsReader implements AccountPaymentsReader {

    private final ControlPlanePaymentService controlPlanePaymentService;

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> listPaymentsForAccount(Long accountId) {
        return controlPlanePaymentService.getPaymentsByAccount(accountId);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentForAccount(Long accountId, Long paymentId) {
        // reusa a validação de escopo do seu service
        if (!controlPlanePaymentService.paymentExistsForAccount(paymentId, accountId)) {
            throw new brito.com.multitenancy001.shared.api.error.ApiException(
                    "PAYMENT_NOT_FOUND", "Pagamento não encontrado", 404
            );
        }
        return controlPlanePaymentService.getPaymentById(paymentId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasActivePayment(Long accountId) {
        return controlPlanePaymentService.hasActivePayment(accountId);
    }
}
