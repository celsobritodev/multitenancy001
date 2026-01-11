package brito.com.multitenancy001.controlplane.api.dto.billing;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AdminPaymentRequest(

        @NotNull
        Long accountId,

        @NotNull
        @DecimalMin(value = "0.01", message = "amount deve ser > 0")
        BigDecimal amount,

        @NotBlank
        String paymentMethod,

        @NotBlank
        String paymentGateway,

        String description
) {}
