package brito.com.multitenancy001.controlplane.billing.api.dto;

import java.math.BigDecimal;

/**
 * Request mínimo para criar/registrar pagamento no ControlPlane.
 *
 * Obs: sem validações BeanValidation aqui para não criar dependência,
 * você pode adicionar @NotNull etc depois se quiser.
 */
public record PaymentCreateRequest(
        Long accountId,
        BigDecimal amount,
        String currency,
        String externalReference,
        String notes
) {
}
