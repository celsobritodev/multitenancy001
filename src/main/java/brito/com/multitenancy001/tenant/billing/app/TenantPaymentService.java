package brito.com.multitenancy001.tenant.billing.app;

import brito.com.multitenancy001.shared.api.error.ApiErrorCode;

import brito.com.multitenancy001.shared.api.dto.billing.PaymentResponse;
import brito.com.multitenancy001.shared.billing.PaymentQueryService;
import brito.com.multitenancy001.shared.executor.PublicSchemaUnitOfWork;
import brito.com.multitenancy001.shared.kernel.error.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TenantPaymentService {

    private final PublicSchemaUnitOfWork publicSchemaUnitOfWork;
    private final PaymentQueryService paymentQueryService;

    /**
     * ✅ Tenant service chamando PUBLIC de forma explícita, sem @Transactional meta-annotation aqui.
     * Semântica: cross-context => boundary é UnitOfWork.
     */
    public List<PaymentResponse> listPaymentsForAccount(Long accountId) {
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);

        return publicSchemaUnitOfWork.readOnly(() -> paymentQueryService.listByAccount(accountId));
    }

    public PaymentResponse getPaymentForAccount(Long accountId, Long paymentId) {
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);
        if (paymentId == null) throw new ApiException(ApiErrorCode.PAYMENT_ID_REQUIRED, "paymentId é obrigatório", 400);

        return publicSchemaUnitOfWork.readOnly(() -> paymentQueryService.getByAccount(accountId, paymentId));
    }

    public boolean hasActivePayment(Long accountId) {
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", 400);

        return publicSchemaUnitOfWork.readOnly(() -> paymentQueryService.hasActivePayment(accountId));
    }
}
