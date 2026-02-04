package brito.com.multitenancy001.shared.api.dto.billing;

import brito.com.multitenancy001.shared.domain.billing.PaymentGateway;
import brito.com.multitenancy001.shared.domain.billing.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AdminPaymentRequest(
        @NotNull Long accountId,

        @NotNull
        @DecimalMin(value = "0.01", message = "amount deve ser > 0")
        BigDecimal amount,

        @NotNull PaymentMethod paymentMethod,
        @NotNull PaymentGateway paymentGateway,

        String description
) {}

