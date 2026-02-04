package brito.com.multitenancy001.shared.billing;

import brito.com.multitenancy001.shared.api.dto.billing.PaymentResponse;
import brito.com.multitenancy001.shared.domain.billing.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Facade de leitura de pagamentos (origem: Control Plane / schema público).
 *
 * Objetivo: permitir que o contexto TENANT consulte pagamentos
 * sem depender diretamente de classes do pacote controlplane.*.
 *
 * Implementação padrão: ControlPlanePaymentQueryService.
 */
public interface PaymentQueryFacade {

    List<PaymentResponse> findByStatus(PaymentStatus status);

    BigDecimal getTotalPaidInPeriod(Long accountId, Instant startDate, Instant endDate);

    long countCompletedPayments(Long accountId);

    List<PaymentResponse> listByAccount(Long accountId);

    PaymentResponse getByAccount(Long accountId, Long paymentId);

    boolean hasActivePayment(Long accountId);
}

