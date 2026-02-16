package brito.com.multitenancy001.controlplane.billing.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Response mínimo para o endpoint de pagamentos.
 * Campos extras podem ser adicionados depois conforme o domínio evoluir.
 */
public record PaymentResponse(
        Long id,
        Long accountId,
        BigDecimal amount,
        String currency,
        String status,
        String externalReference,
        Instant createdAt
) {
}
