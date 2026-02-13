package brito.com.multitenancy001.tenant.billing.app;

import brito.com.multitenancy001.shared.api.dto.billing.PaymentResponse;
import brito.com.multitenancy001.shared.api.error.ApiErrorCode;
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
    private final PaymentQueryService paymentQueryFacade;

    public List<PaymentResponse> listPaymentsForAccount(Long accountId) {
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", ApiErrorCode.ACCOUNT_REQUIRED.defaultHttpStatus());
        return publicSchemaUnitOfWork.readOnly(() -> paymentQueryFacade.listByAccount(accountId));
    }

    public PaymentResponse getPaymentForAccount(Long accountId, Long paymentId) {
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", ApiErrorCode.ACCOUNT_REQUIRED.defaultHttpStatus());
        if (paymentId == null) throw new ApiException(ApiErrorCode.PAYMENT_ID_REQUIRED, "paymentId é obrigatório", ApiErrorCode.PAYMENT_ID_REQUIRED.defaultHttpStatus());

        return publicSchemaUnitOfWork.readOnly(() -> paymentQueryFacade.getByAccount(accountId, paymentId));
    }

    public boolean hasActivePayment(Long accountId) {
        if (accountId == null) throw new ApiException(ApiErrorCode.ACCOUNT_REQUIRED, "accountId é obrigatório", ApiErrorCode.ACCOUNT_REQUIRED.defaultHttpStatus());
        return publicSchemaUnitOfWork.readOnly(() -> paymentQueryFacade.hasActivePayment(accountId));
    }
}
